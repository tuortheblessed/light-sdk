package com.thelightphone.hey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.thelightphone.hey.HeyRepository
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class JournalCalendarScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
    private val initialDay: LocalDate,
) : SimpleLightScreen<LocalDate>(sealedActivity) {

    private val _month = MutableStateFlow(YearMonth.from(initialDay))
    private val monthFlow: StateFlow<YearMonth> = _month.asStateFlow()

    private val _entryDays = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val entryDaysFlow: StateFlow<Set<LocalDate>> = _entryDays.asStateFlow()

    private val _firstWeekDay = MutableStateFlow(DayOfWeek.MONDAY)
    private val firstWeekDayFlow: StateFlow<DayOfWeek> = _firstWeekDay.asStateFlow()

    private val _accountToday = MutableStateFlow(initialDay)
    private val accountTodayFlow: StateFlow<LocalDate> = _accountToday.asStateFlow()

    private val loadedMonths = mutableSetOf<YearMonth>()
    private var loadedDotsGeneration = -1

    private suspend fun ensureMonthLoaded(month: YearMonth) {
        val generation = repository.calendarDotsGeneration()
        if (generation != loadedDotsGeneration) {
            loadedMonths.clear()
            _entryDays.value = emptySet()
            loadedDotsGeneration = generation
        }
        if (month in loadedMonths) return
        loadedMonths.add(month)
        val weekDay = runCatching { repository.firstWeekDay() }.getOrNull()
        if (weekDay != null) _firstWeekDay.value = weekDay
        runCatching { repository.today() }.onSuccess { _accountToday.value = it }
        val days = runCatching { repository.listJournalDaysInMonth(month) }.getOrDefault(emptySet())
        _entryDays.value = _entryDays.value + days
    }

    @Composable
    override fun Content() {
        val month by monthFlow.collectAsState()
        val entryDays by entryDaysFlow.collectAsState()
        val firstWeekDay by firstWeekDayFlow.collectAsState()
        val accountToday by accountTodayFlow.collectAsState()

        LaunchedEffect(month, repository.calendarDotsGeneration()) {
            withContext(Dispatchers.IO) { ensureMonthLoaded(month) }
        }

        MonthCalendarPicker(
            month = month,
            markedDays = entryDays,
            selectedDay = initialDay,
            accountToday = accountToday,
            firstWeekDay = firstWeekDay,
            onMonthChange = { _month.value = it },
            onDaySelected = { goBack(it) },
            onBack = { goBack(null) },
        )
    }
}
