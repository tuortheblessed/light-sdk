package com.thelightphone.hey.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CatalogHabit(
    val id: Long,
    val title: String = "",
    val icon: String = "",
    val iconUrl: String = "",
    val days: List<Int> = emptyList(),
    val stoppedAt: String = "",
    val startsOn: String? = null,
)

internal class HabitCatalogStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): MutableMap<Long, CatalogHabit> {
        val raw = dataStore.data.first()[KEY].orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<List<CatalogHabit>>(raw)
                .associateBy { it.id }
                .toMutableMap()
        }.getOrElse { mutableMapOf() }
    }

    suspend fun save(catalog: Map<Long, CatalogHabit>) {
        val encoded = json.encodeToString(catalog.values.toList())
        dataStore.edit { prefs ->
            prefs[KEY] = encoded
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY)
        }
    }

    fun merge(
        catalog: MutableMap<Long, CatalogHabit>,
        incoming: List<CatalogHabit>,
        preferIncoming: Boolean = true,
    ) {
        for (habit in incoming) {
            if (habit.id == 0L) continue
            val current = catalog[habit.id]
            if (current == null) {
                catalog[habit.id] = habit
                continue
            }
            val chosen = if (preferIncoming || detailScore(habit) >= detailScore(current)) {
                habit
            } else {
                current
            }
            val other = if (chosen === habit) current else habit
            catalog[habit.id] = chosen.copy(
                title = chosen.title.ifBlank { other.title },
                icon = chosen.icon.ifBlank { other.icon },
                iconUrl = chosen.iconUrl.ifBlank { other.iconUrl },
                days = chosen.days.ifEmpty { other.days },
                stoppedAt = chosen.stoppedAt.ifBlank { other.stoppedAt },
                startsOn = chosen.startsOn ?: other.startsOn,
            )
        }
    }

    private fun detailScore(habit: CatalogHabit): Int {
        var score = 0
        if (habit.title.isNotBlank()) score += 2
        if (habit.icon.isNotBlank() || habit.iconUrl.isNotBlank()) score += 2
        if (habit.days.isNotEmpty()) score += 3
        return score
    }

    companion object {
        private val KEY = stringPreferencesKey("hey_habit_catalog_json")
    }
}
