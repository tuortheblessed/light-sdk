package com.thelightphone.hey.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal object AuthKeys {
    val ACCESS_TOKEN = stringPreferencesKey("hey_access_token")
    val REFRESH_TOKEN = stringPreferencesKey("hey_refresh_token")
    val TOKEN_ENDPOINT = stringPreferencesKey("hey_token_endpoint")
}

data class HeyCredentials(
    val accessToken: String,
    val refreshToken: String = "",
    val tokenEndpoint: String = DEFAULT_TOKEN_ENDPOINT,
) {
    val isSignedIn: Boolean get() = accessToken.isNotBlank()

    companion object {
        const val DEFAULT_TOKEN_ENDPOINT = "https://app.hey.com/oauth/tokens"
    }
}

internal class AuthPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): HeyCredentials {
        val prefs = dataStore.data.first()
        return HeyCredentials(
            accessToken = normalizeToken(prefs[AuthKeys.ACCESS_TOKEN].orEmpty()),
            refreshToken = normalizeToken(prefs[AuthKeys.REFRESH_TOKEN].orEmpty()),
            tokenEndpoint = prefs[AuthKeys.TOKEN_ENDPOINT].orEmpty().trim()
                .ifBlank { HeyCredentials.DEFAULT_TOKEN_ENDPOINT },
        )
    }

    suspend fun save(credentials: HeyCredentials) {
        dataStore.edit { prefs ->
            prefs[AuthKeys.ACCESS_TOKEN] = normalizeToken(credentials.accessToken)
            prefs[AuthKeys.REFRESH_TOKEN] = normalizeToken(credentials.refreshToken)
            prefs[AuthKeys.TOKEN_ENDPOINT] = credentials.tokenEndpoint.trim()
                .ifBlank { HeyCredentials.DEFAULT_TOKEN_ENDPOINT }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(AuthKeys.ACCESS_TOKEN)
            prefs.remove(AuthKeys.REFRESH_TOKEN)
            prefs.remove(AuthKeys.TOKEN_ENDPOINT)
        }
    }

    companion object {
        fun normalizeToken(token: String): String {
            var value = token.trim()
            if (value.startsWith("Bearer ", ignoreCase = true)) {
                value = value.substring(7).trim()
            }
            while (value.endsWith('%')) {
                value = value.dropLast(1)
            }
            return value
        }
    }
}
