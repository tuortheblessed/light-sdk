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
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.StickyNote
import com.thelightphone.hey.StickyWriteUnavailableException
import com.thelightphone.hey.ui.components.StickyRow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StickiesViewModel(
    private val repository: HeyRepository,
) : LightViewModel<Unit>() {
    private val _stickies = MutableStateFlow<List<StickyNote>>(emptyList())
    val stickies: StateFlow<List<StickyNote>> = _stickies.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _stickies.value = repository.listStickies()
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "failed to load stickies"
            } finally {
                _loading.value = false
            }
        }
    }

    fun add(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.createSticky(content)
                _stickies.value = repository.listStickies()
            } catch (_: StickyWriteUnavailableException) {
                _errorModal.value = "sticky create not available yet"
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "add sticky failed"
            }
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }
}

class StickiesScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
) : LightScreen<Unit, StickiesViewModel>(sealedActivity) {

    override val viewModelClass: Class<StickiesViewModel>
        get() = StickiesViewModel::class.java

    override fun createViewModel() = StickiesViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val stickies by viewModel.stickies.collectAsState()
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
                            "STICKIES",
                        ),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    when {
                        loading && stickies.isEmpty() -> {
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
                        stickies.isEmpty() -> {
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
                                stickies.forEach { sticky ->
                                    StickyRow(
                                        content = sticky.content,
                                        onClick = {
                                            navigateTo(
                                                screenFactory = {
                                                    StickyDetailScreen(it, repository, sticky)
                                                },
                                            ) {
                                                viewModel.refresh()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    LightBottomBar(
                        listOf(
                            LightBarButton.Text(
                                text = "ADD",
                                onClick = {
                                    navigateTo(
                                        screenFactory = {
                                            TextEditorScreen(
                                                it,
                                                TextEditorRequest(
                                                    title = "NEW STICKY",
                                                    submitLabel = "ADD",
                                                ),
                                            )
                                        },
                                    ) { result ->
                                        val body = result.trim()
                                        if (body.isNotEmpty()) {
                                            viewModel.add(body)
                                        }
                                    }
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
