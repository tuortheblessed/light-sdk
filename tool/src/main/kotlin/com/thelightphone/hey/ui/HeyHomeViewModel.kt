package com.thelightphone.hey.ui

import androidx.lifecycle.viewModelScope
import com.thelightphone.hey.AuthRequiredException
import com.thelightphone.hey.DayOverview
import com.thelightphone.hey.HabitItem
import com.thelightphone.hey.HeyRepository
import com.thelightphone.hey.TodoItem
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object NeedsAuth : HomeUiState()
    data class Ready(val overview: DayOverview) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HeyHomeViewModel(
    private val repository: HeyRepository,
) : LightViewModel<Unit>() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    @Volatile
    private var loadGeneration = 0

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        // App resume and back-navigation both hit show. Refresh quietly when we
        // already have data so external HEY changes (e.g. uncomplete elsewhere) appear
        // without a full loading flash. There is no background poll while idle.
        when (_uiState.value) {
            is HomeUiState.Ready -> viewModelScope.launch(Dispatchers.IO) { refreshQuiet() }
            else -> refresh()
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = HomeUiState.Loading
            try {
                if (!repository.isSignedIn()) {
                    if (generation == loadGeneration) {
                        _uiState.value = HomeUiState.NeedsAuth
                    }
                    return@launch
                }
                val overview = repository.loadDayOverview()
                if (generation == loadGeneration) {
                    _uiState.value = HomeUiState.Ready(overview)
                }
            } catch (_: AuthRequiredException) {
                if (generation == loadGeneration) {
                    _uiState.value = HomeUiState.NeedsAuth
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    _uiState.value = HomeUiState.Error(e.message ?: "failed to load")
                }
            }
        }
    }

    fun toggleHabit(habit: HabitItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                optimisticHabit(habit.id, !habit.doneToday)
                if (habit.doneToday) {
                    repository.uncompleteHabit(habit.id)
                } else {
                    repository.completeHabit(habit.id)
                }
                refreshQuiet()
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "habit update failed"
                refreshQuiet()
            }
        }
    }

    fun toggleTodo(todo: TodoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                optimisticTodo(todo.id, !todo.completed)
                if (todo.completed) {
                    repository.uncompleteTodo(todo.id)
                } else {
                    repository.completeTodo(todo.id)
                }
                refreshQuiet()
            } catch (e: Exception) {
                _errorModal.value = e.message ?: "todo update failed"
                refreshQuiet()
            }
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }

    fun showError(message: String) {
        _errorModal.value = message
    }

    private suspend fun refreshQuiet() {
        val generation = ++loadGeneration
        try {
            if (!repository.isSignedIn()) {
                if (generation == loadGeneration) {
                    _uiState.value = HomeUiState.NeedsAuth
                }
                return
            }
            val overview = repository.loadDayOverview()
            if (generation == loadGeneration) {
                _uiState.value = HomeUiState.Ready(overview)
            }
        } catch (_: AuthRequiredException) {
            if (generation == loadGeneration) {
                _uiState.value = HomeUiState.NeedsAuth
            }
        } catch (_: Exception) {
            // keep current state
        }
    }

    private fun optimisticHabit(id: Long, done: Boolean) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        _uiState.value = HomeUiState.Ready(
            current.overview.copy(
                habits = current.overview.habits.map {
                    if (it.id == id) it.copy(doneToday = done) else it
                },
            ),
        )
    }

    private fun optimisticTodo(id: Long, completed: Boolean) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        _uiState.value = HomeUiState.Ready(
            current.overview.copy(
                todos = current.overview.todos.map {
                    if (it.id == id) it.copy(completed = completed) else it
                }.sortedWith(
                    compareBy<TodoItem> { it.completed }
                        .thenBy { it.createdAt.orEmpty().ifBlank { it.startsAt.orEmpty() }.ifBlank { "9999" } }
                        .thenBy { it.id },
                ),
            ),
        )
    }
}
