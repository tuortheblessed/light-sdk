package com.thelightphone.hey

data class HabitItem(
    val id: Long,
    val title: String,
    val doneToday: Boolean,
    val icon: String = "",
    val iconUrl: String = "",
)

data class TodoItem(
    val id: Long,
    val title: String,
    val startsAt: String?,
    val completed: Boolean,
    val createdAt: String? = null,
)

data class JournalEntry(
    val day: String,
    val content: String,
)

data class StickyNote(
    val noteId: Long,
    val content: String,
    /** Internal only for mutations — never shown in UI. */
    val postingId: Long? = null,
)

data class DayOverview(
    val dayLabel: String,
    val dayIso: String,
    val habits: List<HabitItem>,
    val todos: List<TodoItem>,
    val journal: JournalEntry?,
    val stickies: List<StickyNote>,
)

class AuthRequiredException : Exception("sign in required")

class StickyWriteUnavailableException :
    Exception("sticky create not available yet")

class TodoEditUnavailableException :
    Exception("todo edit not available yet")
