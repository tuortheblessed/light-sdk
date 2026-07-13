package com.thelightphone.hey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.hey.BuildConfig
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.auth.AuthPreferences
import com.thelightphone.hey.auth.HeyCredentials
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: HeyRepository,
    private val filesDir: File,
) : LightViewModel<Unit>() {
    private val _accessToken = MutableStateFlow("")
    val accessToken: StateFlow<String> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow("")
    val refreshToken: StateFlow<String> = _refreshToken.asStateFlow()

    private val _status = MutableStateFlow("—")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            val creds = repository.loadCredentials()
            _accessToken.value = creds.accessToken
            _refreshToken.value = creds.refreshToken
            _status.value = if (creds.isSignedIn) "saved" else "signed out"
        }
    }

    fun setAccessToken(value: String) {
        _accessToken.value = value
    }

    fun setRefreshToken(value: String) {
        _refreshToken.value = value
    }

    /** Loads access token from files/hey_access_token.txt (pushed via adb). Debug only. */
    fun loadTokenFromFile() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(filesDir, TOKEN_FILE_NAME)
            if (!file.exists()) {
                _errorModal.value =
                    "missing $TOKEN_FILE_NAME — push with adb first"
                return@launch
            }
            val token = AuthPreferences.normalizeToken(file.readText())
            if (token.isBlank()) {
                _errorModal.value = "token file is empty"
                return@launch
            }
            _accessToken.value = token
            _status.value = "loaded"
            file.delete()
        }
    }

    fun saveAndVerify() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveCredentials(
                HeyCredentials(
                    accessToken = _accessToken.value,
                    refreshToken = _refreshToken.value,
                    tokenEndpoint = HeyCredentials.DEFAULT_TOKEN_ENDPOINT,
                ),
            )
            val result = repository.verifyAuth()
            if (result.isSuccess) {
                _status.value = "ok"
            } else {
                _status.value = "bad"
                _errorModal.value = result.exceptionOrNull()?.message ?: "auth failed"
            }
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCredentials()
            _accessToken.value = ""
            _refreshToken.value = ""
            _status.value = "signed out"
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }

    companion object {
        const val TOKEN_FILE_NAME = "hey_access_token.txt"
    }
}

class SettingsScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(repository, lightContext.filesDir)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val access by viewModel.accessToken.collectAsState()
        val refresh by viewModel.refreshToken.collectAsState()
        val status by viewModel.status.collectAsState()
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
                        center = LightTopBarCenter.Text("AUTH"),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = "HEY CLI on a computer: “hey auth login”, then “hey auth token” for ACCESS. Optional lasting login: “hey auth status --json” and paste refresh_token as REFRESH.",
                            variant = LightTextVariant.Fine,
                            lighten = true,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                        LightTextField(
                            label = "ACCESS TOKEN",
                            value = access.masked(),
                            placeholder = "paste token",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        TextEditorScreen(
                                            it,
                                            TextEditorRequest("ACCESS TOKEN", access),
                                        )
                                    },
                                ) { result ->
                                    viewModel.setAccessToken(result)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))

                        LightTextField(
                            label = "REFRESH TOKEN",
                            value = refresh.masked(),
                            placeholder = "optional",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        TextEditorScreen(
                                            it,
                                            TextEditorRequest("REFRESH TOKEN", refresh),
                                        )
                                    },
                                ) { result ->
                                    viewModel.setRefreshToken(result)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                        LightText(
                            text = "STATUS  $status",
                            variant = LightTextVariant.Copy,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                        if (BuildConfig.DEBUG) {
                            LightText(
                                text = "LOAD FROM ADB",
                                variant = LightTextVariant.Copy,
                                modifier = Modifier
                                    .lightClickable { viewModel.loadTokenFromFile() }
                                    .padding(vertical = 0.5f.gridUnitsAsDp()),
                            )
                        }
                        LightText(
                            text = "SAVE",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier
                                .lightClickable { viewModel.saveAndVerify() }
                                .padding(vertical = 0.5f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "CLEAR",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier
                                .lightClickable { viewModel.clear() }
                                .padding(vertical = 0.5f.gridUnitsAsDp()),
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

private fun String.masked(): String {
    if (isBlank()) return ""
    if (length <= 8) return "••••"
    return take(4) + "…" + takeLast(4)
}
