package com.thelightphone.hey

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.thelightphone.hey.auth.AuthPreferences
import com.thelightphone.hey.auth.HabitCatalogStore
import com.thelightphone.hey.auth.HeyCredentials
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HeyRepository private constructor(
    private val authPreferences: AuthPreferences,
    private val habitCatalogStore: HabitCatalogStore,
    private val api: HeyApiClient,
) {
    suspend fun isSignedIn(): Boolean = authPreferences.load().isSignedIn

    suspend fun loadCredentials(): HeyCredentials = authPreferences.load()

    suspend fun saveCredentials(credentials: HeyCredentials) {
        authPreferences.save(credentials)
    }

    suspend fun clearCredentials() {
        authPreferences.clear()
        habitCatalogStore.clear()
        api.clearSessionCaches()
    }

    suspend fun verifyAuth(): Result<Unit> = api.ping()

    suspend fun today(): LocalDate = api.today()

    fun calendarDotsGeneration(): Int = api.calendarDotsGeneration

    suspend fun loadDayOverview(day: LocalDate? = null): DayOverview =
        api.loadDayOverview(day)

    suspend fun listHabits(day: LocalDate? = null) = api.listHabits(day)

    suspend fun listWeekTodos() = api.listWeekTodos()

    suspend fun completeHabit(habitId: Long, day: LocalDate? = null) =
        api.completeHabit(habitId, day)

    suspend fun uncompleteHabit(habitId: Long, day: LocalDate? = null) =
        api.uncompleteHabit(habitId, day)

    suspend fun addTodo(title: String) = api.addTodo(title)

    suspend fun completeTodo(todoId: Long) = api.completeTodo(todoId)

    suspend fun uncompleteTodo(todoId: Long) = api.uncompleteTodo(todoId)

    suspend fun getJournal(day: LocalDate? = null): JournalEntry {
        val resolved = day ?: api.today()
        val dayIso = resolved.toString()
        return api.getJournal(dayIso) ?: JournalEntry(dayIso, "")
    }

    suspend fun updateJournal(content: String, day: LocalDate? = null) {
        val resolved = day ?: api.today()
        api.updateJournal(resolved.toString(), content)
    }

    suspend fun listStickies() = api.listStickies()

    suspend fun listJournalDays(): Set<LocalDate> = api.listJournalDays()

    suspend fun listJournalDaysInMonth(month: YearMonth): Set<LocalDate> =
        api.listJournalDaysInMonth(month)

    suspend fun listHabitCompletionDays(): Set<LocalDate> = api.listHabitCompletionDays()

    suspend fun listHabitCompletionDaysInMonth(month: YearMonth): Set<LocalDate> =
        api.listHabitCompletionDaysInMonth(month)

    suspend fun firstWeekDay(): DayOfWeek = api.firstWeekDay()

    suspend fun createSticky(content: String) = api.createSticky(content)

    suspend fun updateSticky(note: StickyNote, content: String) =
        api.updateSticky(note.noteId, content, note.postingId)

    suspend fun tryUpdateTodoTitle(todoId: Long, title: String): Boolean {
        return try {
            api.updateTodoTitle(todoId, title)
            true
        } catch (_: TodoEditUnavailableException) {
            false
        }
    }

    companion object {
        @Volatile
        private var instance: HeyRepository? = null

        fun getInstance(dataStore: DataStore<Preferences>): HeyRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val auth = AuthPreferences(dataStore)
                    val catalog = HabitCatalogStore(dataStore)
                    val api = HeyApiClient(auth, catalog)
                    HeyRepository(auth, catalog, api).also { instance = it }
                }
            }
        }
    }
}
