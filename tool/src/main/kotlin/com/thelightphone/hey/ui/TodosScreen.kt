package com.thelightphone.hey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.TodoItem
import com.thelightphone.hey.ui.components.TodoRow
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TodosViewModel(
    private val repository: HeyRepository,
) : LightViewModel<Unit>() {
    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    @Volatile
    private var loadGeneration = 0

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        refresh()
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val todos = repository.listWeekTodos()
                if (generation == loadGeneration) {
                    _todos.value = todos
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    _errorModal.value = e.message ?: "failed to load todos"
                }
            } finally {
                if (generation == loadGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    fun toggle(todo: TodoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _todos.value = _todos.value.map {
                    if (it.id == todo.id) it.copy(completed = !todo.completed) else it
                }.sortedWith(todoWeekOrder)
                if (todo.completed) {
                    repository.uncompleteTodo(todo.id)
                } else {
                    repository.completeTodo(todo.id)
                }
                val generation = loadGeneration
                val todos = repository.listWeekTodos()
                if (generation == loadGeneration) {
                    _todos.value = todos
                }
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "todo update failed"
                refresh()
            }
        }
    }

    fun add(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addTodo(title)
                _todos.value = repository.listWeekTodos()
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "add todo failed"
            }
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }

    fun showError(message: String) {
        _errorModal.value = message
    }
}

private val todoWeekOrder = compareBy<TodoItem> { it.completed }
    .thenBy { it.createdAt.orEmpty().ifBlank { it.startsAt.orEmpty() }.ifBlank { "9999" } }
    .thenBy { it.id }

class TodosScreen(
    sealedActivity: SealedLightActivity,
    private val repository: HeyRepository,
) : LightScreen<Unit, TodosViewModel>(sealedActivity) {

    override val viewModelClass: Class<TodosViewModel>
        get() = TodosViewModel::class.java

    override fun createViewModel() = TodosViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val todos by viewModel.todos.collectAsState()
        val loading by viewModel.loading.collectAsState()
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
                        center = LightTopBarCenter.Text("SOMETIME THIS WEEK"),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    when {
                        loading && todos.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = "loading…",
                                    variant = LightTextVariant.Copy,
                                    lighten = true,
                                )
                            }
                        }
                        todos.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = "none this week",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                    lighten = true,
                                )
                            }
                        }
                        else -> {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                scrollBarPosition = LightScrollBarPosition.Inside,
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        start = 1f.gridUnitsAsDp(),
                                        end = 2.5f.gridUnitsAsDp(),
                                    ),
                                ) {
                                    todos.forEach { todo ->
                                        TodoRow(
                                            title = todo.title,
                                            completed = todo.completed,
                                            onClick = { viewModel.toggle(todo) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LightBottomBar(
                        listOf(
                            LightBarButton.Text(
                                text = "ADD",
                                onClick = {
                                    navigateTo(
                                        screenFactory = {
                                            TextEditorScreen(
                                                it,
                                                TextEditorRequest(
                                                    title = "NEW TODO",
                                                    submitLabel = "ADD",
                                                ),
                                            )
                                        },
                                    ) { result ->
                                        val title = result.trim()
                                        if (title.isNotEmpty()) {
                                            viewModel.add(title)
                                        }
                                    }
                                },
                            ),
                        ),
                    )
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
