package com.leo.checkertic.data.model

import androidx.compose.runtime.Immutable
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.TaskEntity

/**
 * Sort options for tasks, notes and categories.
 *
 * Pinning is *not* a sort mode. It composes with whichever mode is active —
 * pinned items float above unpinned ones regardless — which is how every app
 * that has both behaves, and the only behaviour that makes pinning useful.
 */
enum class SortMode(val id: String, val label: String) {
    MANUAL("manual", "Manual order"),
    ALPHABETICAL("alpha", "A–Z"),
    CREATED("created", "Date created"),
    MODIFIED("modified", "Date modified");

    companion object {
        val Default = MANUAL
        fun from(id: String?): SortMode = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * Comparators.
 *
 * Every one of these is `pinned first, then the chosen key, then id`. The
 * trailing id tiebreak matters more than it looks: without it, two notes
 * saved in the same millisecond can swap places between emissions, and a
 * LazyColumn keyed by id will animate the swap. A stable total order means a
 * list that only moves when something actually changed.
 */
object Sorting {

    fun tasks(mode: SortMode): Comparator<TaskEntity> {
        val key: Comparator<TaskEntity> = when (mode) {
            SortMode.MANUAL -> compareBy { it.orderIndex }
            SortMode.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortMode.CREATED -> compareByDescending { it.createdAt }
            SortMode.MODIFIED -> compareByDescending { it.updatedAt }
        }
        return compareByDescending<TaskEntity> { it.pinned }
            .then(key)
            .thenByDescending(TaskEntity::id)
    }

    /** Completed tasks always read newest-finished-first, whatever the list sort is. */
    val completedTasks: Comparator<TaskEntity> =
        compareByDescending<TaskEntity> { it.completedAt ?: 0L }
            .thenByDescending(TaskEntity::id)

    fun notes(mode: SortMode): Comparator<NoteEntity> {
        val key: Comparator<NoteEntity> = when (mode) {
            SortMode.MANUAL -> compareBy { it.orderIndex }
            SortMode.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortMode.CREATED -> compareByDescending { it.createdAt }
            SortMode.MODIFIED -> compareByDescending { it.updatedAt }
        }
        return compareByDescending<NoteEntity> { it.pinned }
            .then(key)
            .thenByDescending(NoteEntity::id)
    }

    fun categories(mode: SortMode): Comparator<CategoryEntity> {
        val key: Comparator<CategoryEntity> = when (mode) {
            SortMode.MANUAL -> compareBy { it.orderIndex }
            SortMode.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.CREATED -> compareByDescending { it.createdAt }
            SortMode.MODIFIED -> compareByDescending { it.updatedAt }
        }
        return compareByDescending<CategoryEntity> { it.pinned }
            .then(key)
            .thenByDescending(CategoryEntity::id)
    }
}

/** Whether task search looks at one category or all of them. */
enum class SearchScope(val label: String) {
    CATEGORY("This list"),
    ALL("Everywhere")
}

@Immutable
data class TaskSearchHit(
    val task: TaskEntity,
    val categoryName: String
)

@Immutable
data class SearchResults(
    val query: String = "",
    val tasks: List<TaskSearchHit> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val searching: Boolean = false
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && notes.isEmpty()
    val total: Int get() = tasks.size + notes.size
}
