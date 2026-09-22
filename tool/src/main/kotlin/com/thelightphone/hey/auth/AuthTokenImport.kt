package com.thelightphone.hey.auth

import com.thelightphone.hey.HeyRepository
import com.thelightphone.sdk.LightFileShare

internal object AuthTokenImport {
    const val DIR = "auth"
    const val ACCESS_FILE = "auth/access_token.txt"
    const val REFRESH_FILE = "auth/refresh_token.txt"

    /**
     * Reads Tool Manager-uploaded token files into DataStore, then deletes them
     * so they are not left in the shared directory. Returns true if credentials changed.
     */
    suspend fun importIfPresent(
        fileShare: LightFileShare,
        repository: HeyRepository,
    ): Boolean {
        val accessRaw = fileShare.read(ACCESS_FILE) { it.readText() }
        val refreshRaw = fileShare.read(REFRESH_FILE) { it.readText() }
        if (accessRaw == null && refreshRaw == null) return false

        val current = repository.loadCredentials()
        val access = accessRaw?.let { AuthPreferences.normalizeToken(it) }
            .orEmpty()
            .ifBlank { current.accessToken }
        val refresh = refreshRaw?.let { AuthPreferences.normalizeToken(it) }
            .orEmpty()
            .ifBlank { current.refreshToken }
        if (access.isBlank()) return false

        repository.saveCredentials(
            HeyCredentials(
                accessToken = access,
                refreshToken = refresh,
                tokenEndpoint = HeyCredentials.DEFAULT_TOKEN_ENDPOINT,
            ),
        )
        fileShare.delete(ACCESS_FILE)
        fileShare.delete(REFRESH_FILE)
        return true
    }
}
