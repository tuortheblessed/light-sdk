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
import com.thelightphone.sdk.NetworkStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.hey.DayOverview
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.StickyNote
import com.thelightphone.hey.ui.components.EmptyHint
import com.thelightphone.hey.ui.components.HabitRow
import com.thelightphone.hey.ui.components.MoreRow
import com.thelightphone.hey.ui.components.SectionHeader
import com.thelightphone.hey.ui.components.StickyRow
import com.thelightphone.hey.ui.components.TodoRow
import com.thelightphone.hey.ui.components.singleLinePreview
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

private const val HABIT_PREVIEW_CAP = 4
private const val TODO_PREVIEW_CAP = 2
private const val STICKY_PREVIEW_CAP = 2

@InitialScreen
class HeyHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HeyHomeViewModel>(sealedActivity) {

    private val repository = HeyRepository.getInstance(lightContext.dataStore)

    override val viewModelClass: Class<HeyHomeViewModel>
        get() = HeyHomeViewModel::class.java

    override fun createViewModel() = HeyHomeViewModel(repository, lightContext.fileShare)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()
        val network by lightContext.connectivity.observeNetworkStatus().collectAsState(
            initial = NetworkStatus(isConnected = true, isWifi = false, isMetered = false),
        )

        LightTheme(colors = themeColors) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    LightTopBar(
                        center = LightTopBarCenter.Text("HEY"),
                        rightButton = LightBarButton.LightIcon(
                            icon = LightIcons.SETTINGS,
                            sizeUnits = 1f,
                            onClick = {
                                navigateTo(screenFactory = { SettingsScreen(it, repository) }) {
                                    viewModel.refresh()
                                }
                            },
                        ),
                        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                    )
                    if (!network.isConnected) {
                        LightText(
                            text = "offline",
                            variant = LightTextVariant.Fine,
                            lighten = true,
                            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                    }

                    when (val state = uiState) {
                        HomeUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = "loading…",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                    lighten = true,
                                )
                            }
                        }
                        HomeUiState.NeedsAuth -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LightText(
                                        text = "sign in required",
                                        variant = LightTextVariant.Copy,
                                        align = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                                    LightText(
                                        text = "SETTINGS",
                                        variant = LightTextVariant.Copy,
                                        modifier = Modifier.lightClickable {
                                            navigateTo(
                                                screenFactory = { SettingsScreen(it, repository) },
                                            ) {
                                                viewModel.refresh()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        is HomeUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = state.message,
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                    lighten = true,
                                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                                )
                            }
                        }
                        is HomeUiState.Ready -> {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                scrollBarPosition = LightScrollBarPosition.Inside,
                            ) {
                                OverviewBody(
                                    overview = state.overview,
                                    modifier = Modifier.padding(
                                        start = 1f.gridUnitsAsDp(),
                                        // Keep counts clear of the inside scrollbar.
                                        end = 2.5f.gridUnitsAsDp(),
                                    ),
                                )
                            }
                        }
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

    @Composable
    private fun OverviewBody(
        overview: DayOverview,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier = modifier) {
            LightText(
                text = overview.dayLabel,
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp()),
            )

            HabitsSection(overview)
            Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))
            TodosSection(overview)
            Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))
            JournalSection(overview)
            Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))
            StickiesSection(overview)
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
        }
    }

    @Composable
    private fun HabitsSection(overview: DayOverview) {
        val habits = overview.habits
        val done = habits.count { it.doneToday }
        SectionHeader(
            label = "HABITS",
            count = "$done/${habits.size}",
            onClick = { openHabits() },
        )
        if (habits.isEmpty()) {
            EmptyHint("none today")
        } else {
            habits.take(HABIT_PREVIEW_CAP).forEach { habit ->
                HabitRow(
                    title = habit.title,
                    done = habit.doneToday,
                    iconSlug = habit.icon,
                    onClick = { viewModel.toggleHabit(habit) },
                )
            }
            val remaining = habits.size - HABIT_PREVIEW_CAP
            if (remaining > 0) {
                MoreRow(remaining = remaining, onClick = { openHabits() })
            }
        }
    }

    @Composable
    private fun TodosSection(overview: DayOverview) {
        val todos = overview.todos
        SectionHeader(
            label = "SOMETIME THIS WEEK",
            count = todos.size.toString(),
            onClick = { openTodos() },
        )
        if (todos.isEmpty()) {
            EmptyHint("none this week")
        } else {
            todos.take(TODO_PREVIEW_CAP).forEach { todo ->
                TodoRow(
                    title = todo.title,
                    completed = todo.completed,
                    onClick = { viewModel.toggleTodo(todo) },
                )
            }
            val remaining = todos.size - TODO_PREVIEW_CAP
            if (remaining > 0) {
                MoreRow(remaining = remaining, onClick = { openTodos() })
            }
        }
    }

    @Composable
    private fun JournalSection(overview: DayOverview) {
        val content = overview.journal?.content.orEmpty().trim()
        SectionHeader(
            label = "JOURNAL",
            count = if (content.isEmpty()) "SET" else null,
            onClick = { openJournal() },
        )
        if (content.isEmpty()) {
            EmptyHint("—")
        } else {
            LightText(
                text = content.singleLinePreview(72),
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable { openJournal() }
                    .padding(vertical = 0.35f.gridUnitsAsDp()),
            )
        }
    }

    @Composable
    private fun StickiesSection(overview: DayOverview) {
        val stickies = overview.stickies
        SectionHeader(
            label = "STICKIES",
            count = stickies.size.toString(),
            onClick = { openStickies() },
        )
        if (stickies.isEmpty()) {
            EmptyHint("none")
        } else {
            stickies.take(STICKY_PREVIEW_CAP).forEach { sticky ->
                StickyRow(
                    content = sticky.content,
                    onClick = { openStickyDetail(sticky) },
                )
            }
            val remaining = stickies.size - STICKY_PREVIEW_CAP
            if (remaining > 0) {
                MoreRow(remaining = remaining, onClick = { openStickies() })
            }
        }
    }

    private fun openHabits() {
        navigateTo(screenFactory = { HabitsScreen(it, repository) }) {
            viewModel.refresh()
        }
    }

    private fun openTodos() {
        navigateTo(screenFactory = { TodosScreen(it, repository) }) {
            viewModel.refresh()
        }
    }

    private fun openJournal() {
        navigateTo(screenFactory = { JournalScreen(it, repository) }) {
            viewModel.refresh()
        }
    }

    private fun openStickies() {
        navigateTo(screenFactory = { StickiesScreen(it, repository) }) {
            viewModel.refresh()
        }
    }

    private fun openStickyDetail(sticky: StickyNote) {
        navigateTo(screenFactory = { StickyDetailScreen(it, repository, sticky) }) {
            viewModel.refresh()
        }
    }
}
