package com.leo.checkertic.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.repository.CategoryRepository
import com.leo.checkertic.data.repository.TaskRepository
import com.leo.checkertic.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val categoryRepo = CategoryRepository(db.categoryDao())
    private val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())

    val categories: StateFlow<List<CategoryEntity>> = categoryRepo.getAllOrdered()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId

    init {
        viewModelScope.launch {
            categories.collect { list ->
                if (_selectedCategoryId.value == null && list.isNotEmpty()) {
                    _selectedCategoryId.value = list.first().id
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<TaskEntity>> = _selectedCategoryId
        .flatMapLatest { catId ->
            if (catId != null) {
                taskRepo.getIncompleteTasksForCategory(catId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allTasks: StateFlow<List<TaskEntity>> = _selectedCategoryId
        .flatMapLatest { catId ->
            if (catId != null) {
                taskRepo.getTasksForCategory(catId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCategory(id: Long) {
        _selectedCategoryId.value = id
        viewModelScope.launch {
            taskRepo.ensureRecurrenceReset(id)
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val currentCategories = categories.value
            val nextIndex = if (currentCategories.isEmpty()) 0 else currentCategories.maxOf { it.orderIndex } + 1
            val id = categoryRepo.insert(
                CategoryEntity(name = name, orderIndex = nextIndex)
            )
            _selectedCategoryId.value = id
            WidgetUpdater.update(getApplication())
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepo.delete(category)
            if (_selectedCategoryId.value == category.id) {
                _selectedCategoryId.value = categories.value.firstOrNull()?.id
            }
            WidgetUpdater.update(getApplication())
        }
    }

    fun renameCategory(category: CategoryEntity, newName: String) {
        viewModelScope.launch {
            categoryRepo.update(category.copy(name = newName))
            WidgetUpdater.update(getApplication())
        }
    }

    fun reorderCategories(reorderedList: List<CategoryEntity>) {
        viewModelScope.launch {
            val updates = reorderedList.mapIndexed { index, cat -> cat.id to index }
            categoryRepo.updateOrderIndexes(updates)
            WidgetUpdater.update(getApplication())
        }
    }

    fun addTask(title: String) {
        val categoryId = _selectedCategoryId.value ?: return
        viewModelScope.launch {
            taskRepo.addTask(title, categoryId)
            launch(Dispatchers.IO) {
                WidgetUpdater.update(getApplication())
            }
        }
    }

    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            taskRepo.completeTask(taskId)
            WidgetUpdater.update(getApplication())
        }
    }

    fun uncompleteTask(taskId: Long) {
        viewModelScope.launch {
            taskRepo.uncompleteTask(taskId)
            WidgetUpdater.update(getApplication())
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.deleteTask(task)
            WidgetUpdater.update(getApplication())
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            if (task.completed) {
                taskRepo.uncompleteTask(task.id)
            } else {
                taskRepo.completeTask(task.id)
            }
            WidgetUpdater.update(getApplication())
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val all = allTasks.value
            all.filter { it.completed }.forEach { taskRepo.deleteTask(it) }
            WidgetUpdater.update(getApplication())
        }
    }

    fun updateRecurrence(categoryId: Long, type: String, customDays: Int = 0) {
        viewModelScope.launch {
            categoryRepo.updateRecurrence(categoryId, type, customDays)
            WidgetUpdater.update(getApplication())
        }
    }
}
