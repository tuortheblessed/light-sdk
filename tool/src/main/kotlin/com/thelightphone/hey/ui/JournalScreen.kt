package com.thelightphone.hey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.hey.HeyApiClient
import com.thelightphone.hey.HeyRepository
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JournalViewModel(
    private val repository: HeyRepository,
) : LightViewModel<Unit>() {
    private val _day = MutableStateFlow(LocalDate.now())
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _dayLabel = MutableStateFlow("")
    val dayLabel: StateFlow<String> = _dayLabel.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    @Volatile
    private var loadGeneration = 0

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        // Returning from the editor triggers show + save together; don't clobber save.
        if (!_saving.value) {
            viewModelScope.launch(Dispatchers.IO) {
                if (_dayLabel.value.isBlank()) {
                    loadDay(repository.today())
                } else {
                    loadDay(_day.value)
                }
            }
        }
    }

    fun refresh() {
        loadDay(_day.value)
    }

    fun goToPreviousDay() {
        if (_saving.value) return
        loadDay(_day.value.minusDays(1))
    }

    fun goToNextDay() {
        if (_saving.value) return
        loadDay(_day.value.plusDays(1))
    }

    fun goToToday() {
        if (_saving.value) return
        viewModelScope.launch(Dispatchers.IO) {
            loadDay(repository.today())
        }
    }

    fun selectDay(day: LocalDate) {
        if (_saving.value) return
        loadDay(day)
    }

    private fun loadDay(day: LocalDate) {
        val generation = ++loadGeneration
        _day.value = day
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _dayLabel.value = HeyApiClient.formatDayLabel(day)
                val remote = repository.getJournal(day).content
                if (generation == loadGeneration) {
                    _content.value = remote
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    _errorModal.value = e.message ?: "failed to load journal"
                }
            } finally {
                if (generation == loadGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    fun save(content: String) {
        val generation = ++loadGeneration
        val day = _day.value
        _saving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateJournal(content, day)
                val remote = repository.getJournal(day).content
                if (generation == loadGeneration) {
                    _content.value = remote
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    _errorModal.value = e.message ?: "journal save failed"
                }
            } finally {
                if (generation == loadGeneration) {
                    _saving.value = false
                    _loading.value = false
                }
            }
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }
}

class JournalScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
) : LightScreen<Unit, JournalViewModel>(sealedActivity) {

    override val viewModelClass: Class<JournalViewModel>
        get() = JournalViewModel::class.java

    override fun createViewModel() = JournalViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val content by viewModel.content.collectAsState()
        val dayLabel by viewModel.dayLabel.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()
        val saving by viewModel.saving.collectAsState()
        val actionLabel = when {
            saving -> "SAVING…"
            content.isBlank() -> "WRITE"
            else -> "EDIT"
        }

        LightTheme(colors = themeColors) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack(null) },
                        ),
                        center = LightTopBarCenter.Text(
                            text = if (dayLabel.isBlank()) "JOURNAL" else "JOURNAL · $dayLabel",
                            onClick = {
                                val current = viewModel.day.value
                                navigateTo(
                                    screenFactory = {
                                        JournalCalendarScreen(it, repository, current)
                                    },
                                ) { picked ->
                                    viewModel.selectDay(picked)
                                }
                            },
                        ),
                        rightButton = LightBarButton.Text(
                            text = actionLabel,
                            onClick = {
                                if (saving) return@Text
                                navigateTo(
                                    screenFactory = {
                                        TextEditorScreen(
                                            it,
                                            TextEditorRequest(
                                                title = "JOURNAL",
                                                initialValue = content,
                                                submitLabel = "SAVE",
                                                editorKey = "journal-${System.nanoTime()}",
                                            ),
                                        )
                                    },
                                ) { result ->
                                    viewModel.save(result)
                                }
                            },
                        ),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    when {
                        loading && content.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = "loading…",
                                    variant = LightTextVariant.Copy,
                                    lighten = true,
                                )
                            }
                        }
                        content.isBlank() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = "—",
                                    variant = LightTextVariant.Copy,
                                    lighten = true,
                                )
                            }
                        }
                        else -> {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = content,
                                    variant = LightTextVariant.Paragraph,
                                )
                            }
                        }
                    }

                    LightBottomBar(
                        listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.DIRECTIONS_LEFT,
                                contentDescription = "Previous day",
                                onClick = {
                                    if (!saving) viewModel.goToPreviousDay()
                                },
                            ),
                            LightBarButton.Text(
                                text = "TODAY",
                                onClick = {
                                    if (!saving) viewModel.goToToday()
                                },
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.DIRECTIONS_RIGHT,
                                contentDescription = "Next day",
                                onClick = {
                                    if (!saving) viewModel.goToNextDay()
                                },
                            ),
                        ),
                    )
                }

                errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
    }
}
