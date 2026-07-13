package com.thelightphone.hey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendarPicker(
    month: YearMonth,
    markedDays: Set<LocalDate>,
    selectedDay: LocalDate,
    accountToday: LocalDate,
    firstWeekDay: DayOfWeek,
    onMonthChange: (YearMonth) -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.US).uppercase(Locale.US) +
        " ${month.year}"
    val weekdayLabels = weekdayLabelsStarting(firstWeekDay)

    LightTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onBack,
                ),
                center = LightTopBarCenter.Text(monthLabel),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LightText(
                    text = "◀",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.lightClickable {
                        onMonthChange(month.minusMonths(1))
                    },
                )
                LightText(
                    text = "▶",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.lightClickable {
                        onMonthChange(month.plusMonths(1))
                    },
                )
            }

            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekdayLabels.forEach { label ->
                    LightText(
                        text = label,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

            val first = month.atDay(1)
            val startOffset = (first.dayOfWeek.value - firstWeekDay.value + 7) % 7
            val daysInMonth = month.lengthOfMonth()
            val cells = buildList {
                repeat(startOffset) { add(null as LocalDate?) }
                for (day in 1..daysInMonth) add(month.atDay(day))
                while (size % 7 != 0) add(null)
            }

            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3f.gridUnitsAsDp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                val hasMark = markedDays.contains(day)
                                val isSelected = day == selectedDay
                                val isToday = day == accountToday
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LightText(
                                        text = day.dayOfMonth.toString(),
                                        variant = if (isSelected || isToday) {
                                            LightTextVariant.Copy
                                        } else {
                                            LightTextVariant.Detail
                                        },
                                        lighten = !hasMark && !isSelected,
                                        align = TextAlign.Center,
                                        modifier = Modifier.lightClickable {
                                            onDaySelected(day)
                                        },
                                    )
                                    if (hasMark) {
                                        LightText(
                                            text = "·",
                                            variant = LightTextVariant.Detail,
                                            align = TextAlign.Center,
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(1f.gridUnitsAsDp()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun weekdayLabelsStarting(first: DayOfWeek): List<String> {
    val order = DayOfWeek.values()
    val start = order.indexOf(first).coerceAtLeast(0)
    return (0 until 7).map { offset ->
        order[(start + offset) % 7]
            .getDisplayName(TextStyle.NARROW, Locale.US)
            .uppercase(Locale.US)
    }
}
