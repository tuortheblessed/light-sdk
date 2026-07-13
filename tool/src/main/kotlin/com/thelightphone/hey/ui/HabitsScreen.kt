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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.hey.HabitItem
import com.thelightphone.hey.HeyApiClient
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.ui.components.HabitRow
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

class HabitsViewModel(
    private val repository: HeyRepository,
) : LightViewModel<Unit>() {
    private val _day = MutableStateFlow(LocalDate.now())
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    private val _habits = MutableStateFlow<List<HabitItem>>(emptyList())
    val habits: StateFlow<List<HabitItem>> = _habits.asStateFlow()

    private val _dayLabel = MutableStateFlow("")
    val dayLabel: StateFlow<String> = _dayLabel.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    @Volatile
    private var loadGeneration = 0

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_dayLabel.value.isBlank()) {
                loadDay(repository.today())
            } else {
                loadDay(_day.value)
            }
        }
    }

    fun refresh() {
        loadDay(_day.value)
    }

    fun goToPreviousDay() {
        loadDay(_day.value.minusDays(1))
    }

    fun goToNextDay() {
        loadDay(_day.value.plusDays(1))
    }

    fun goToToday() {
        viewModelScope.launch(Dispatchers.IO) {
            loadDay(repository.today())
        }
    }

    fun selectDay(day: LocalDate) {
        loadDay(day)
    }

    private fun loadDay(day: LocalDate) {
        val generation = ++loadGeneration
        _day.value = day
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _dayLabel.value = HeyApiClient.formatDayLabel(day)
                val habits = repository.listHabits(day)
                if (generation == loadGeneration) {
                    _habits.value = habits
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    _errorModal.value = e.message ?: "failed to load habits"
                }
            } finally {
                if (generation == loadGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    fun toggle(habit: HabitItem) {
        val day = _day.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _habits.value = _habits.value.map {
                    if (it.id == habit.id) it.copy(doneToday = !habit.doneToday) else it
                }
                if (habit.doneToday) {
                    repository.uncompleteHabit(habit.id, day)
                } else {
                    repository.completeHabit(habit.id, day)
                }
                _habits.value = repository.listHabits(day)
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "habit update failed"
                refresh()
            }
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }
}

class HabitsScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
) : LightScreen<Unit, HabitsViewModel>(sealedActivity) {

    override val viewModelClass: Class<HabitsViewModel>
        get() = HabitsViewModel::class.java

    override fun createViewModel() = HabitsViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val habits by viewModel.habits.collectAsState()
        val dayLabel by viewModel.dayLabel.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()

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
                            text = if (dayLabel.isBlank()) "HABITS" else "HABITS · $dayLabel",
                            onClick = {
                                val current = viewModel.day.value
                                navigateTo(
                                    screenFactory = {
                                        HabitsCalendarScreen(it, repository, current)
                                    },
                                ) { picked ->
                                    viewModel.selectDay(picked)
                                }
                            },
                        ),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    when {
                        loading && habits.isEmpty() -> {
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
                        habits.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = "none",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                    lighten = true,
                                )
                            }
                        }
                        else -> {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 1f.gridUnitsAsDp()),
                            ) {
                                habits.forEach { habit ->
                                    HabitRow(
                                        title = habit.title,
                                        done = habit.doneToday,
                                        iconSlug = habit.icon,
                                        onClick = { viewModel.toggle(habit) },
                                    )
                                }
                            }
                        }
                    }

                    LightBottomBar(
                        listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.DIRECTIONS_LEFT,
                                contentDescription = "Previous day",
                                onClick = { viewModel.goToPreviousDay() },
                            ),
                            LightBarButton.Text(
                                text = "TODAY",
                                onClick = { viewModel.goToToday() },
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.DIRECTIONS_RIGHT,
                                contentDescription = "Next day",
                                onClick = { viewModel.goToNextDay() },
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
