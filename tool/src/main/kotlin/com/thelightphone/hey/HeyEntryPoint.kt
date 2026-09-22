package com.thelightphone.hey

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@EntryPoint
object HeyEntryPoint : LightEntryPoint {
    private val _authImportGeneration = MutableStateFlow(0)
    val authImportGeneration: StateFlow<Int> = _authImportGeneration.asStateFlow()

    override fun getToolManagerManifest(): ClientToolManifest {
        return ClientToolManifest(
            title = "HEY",
            roots = listOf(
                ClientLeafNode(
                    FileBrowserSpec(
                        label = "AUTH",
                        path = "auth",
                        headerText = "Upload access_token.txt from “hey auth token”. Optional lasting login: upload refresh_token.txt with the refresh_token value from “hey auth status --json”. Then open HEY.",
                    ),
                ),
            ),
        )
    }

    override suspend fun onToolManagerDataUpdate() {
        _authImportGeneration.value = _authImportGeneration.value + 1
    }
}
