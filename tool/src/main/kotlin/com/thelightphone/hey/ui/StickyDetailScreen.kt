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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.StickyNote
import com.thelightphone.hey.StickyWriteUnavailableException
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
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

class StickyDetailViewModel(
    private val repository: HeyRepository,
    initial: StickyNote,
) : LightViewModel<Unit>() {
    private val _note = MutableStateFlow(initial)
    val note: StateFlow<StickyNote> = _note.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    private val _editAvailable = MutableStateFlow(true)
    val editAvailable: StateFlow<Boolean> = _editAvailable.asStateFlow()

    fun save(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateSticky(_note.value, content)
                _note.value = _note.value.copy(content = content)
            } catch (_: StickyWriteUnavailableException) {
                _editAvailable.value = false
                _errorModal.value = "sticky edit not available yet"
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "sticky save failed"
            }
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }
}

class StickyDetailScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
    private val sticky: StickyNote,
) : LightScreen<Unit, StickyDetailViewModel>(sealedActivity) {

    override val viewModelClass: Class<StickyDetailViewModel>
        get() = StickyDetailViewModel::class.java

    override fun createViewModel() = StickyDetailViewModel(repository, sticky)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val note by viewModel.note.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()
        val editAvailable by viewModel.editAvailable.collectAsState()

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
                        center = LightTopBarCenter.Text("STICKY"),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = note.content,
                            variant = LightTextVariant.Paragraph,
                        )
                    }

                    if (editAvailable) {
                        LightBottomBar(
                            listOf(
                                LightBarButton.Text(
                                    text = "EDIT",
                                    onClick = {
                                        navigateTo(
                                            screenFactory = {
                                                TextEditorScreen(
                                                    it,
                                                    TextEditorRequest(
                                                        title = "STICKY",
                                                        initialValue = note.content,
                                                        submitLabel = "SAVE",
                                                    ),
                                                )
                                            },
                                        ) { result ->
                                            viewModel.save(result)
                                        }
                                    },
                                ),
                            ),
                        )
                    }
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
