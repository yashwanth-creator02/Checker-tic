package com.leo.checkertic.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.model.SortMode

/**
 * ============================================================================
 *  DISPLAY MODELS
 * ============================================================================
 *
 * What the UI actually renders. Three reasons these exist rather than passing
 * entities straight to composables:
 *
 *  1. **Decryption happens once, off the main thread.** A locked task's title
 *     is an AES operation. Passing the entity down would mean decrypting
 *     inside a composable, on every recomposition, on the main thread.
 *  2. **Sorting and splitting happen once.** The ViewModel produces the
 *     active list and the completed list already ordered; the screen never
 *     filters or sorts during composition.
 *  3. **`@Immutable` makes skipping work.** Compose can skip a row whose
 *     model is `equals` to last frame's, which is what stops one tick from
 *     recomposing an entire list.
 */

@Immutable
data class TaskUi(
    val id: Long,
    val title: String,
    val completed: Boolean,
    val pinned: Boolean,
    val categoryId: Long,
    val completedAt: Long?,
    val hasReminder: Boolean,
    /** True when this row's text could not be decrypted right now. */
    val obscured: Boolean
)

@Immutable
data class CategoryUi(
    val id: Long,
    val name: String,
    val pinned: Boolean,
    val locked: Boolean,
    val background: String?,
    val recurrenceType: String,
    val recurrenceCustomDays: Int,
    val sortMode: SortMode,
    val hasReminder: Boolean
) {
    companion object {
        fun from(entity: CategoryEntity, hasReminder: Boolean) = CategoryUi(
            id = entity.id,
            name = entity.name,
            pinned = entity.pinned,
            locked = entity.locked,
            background = entity.background,
            recurrenceType = entity.recurrenceType,
            recurrenceCustomDays = entity.recurrenceCustomDays,
            sortMode = SortMode.from(entity.sortMode),
            hasReminder = hasReminder
        )
    }
}

@Immutable
data class TasksUiState(
    val categories: List<CategoryUi> = emptyList(),
    val selected: CategoryUi? = null,
    val active: List<TaskUi> = emptyList(),
    val completed: List<TaskUi> = emptyList(),
    /**
     * True when the selected category is locked and the vault is shut, so the
     * screen shows the unlock prompt instead of a list of dots.
     */
    val needsUnlock: Boolean = false,
    val loading: Boolean = true
) {
    val hasCompleted: Boolean get() = completed.isNotEmpty()
}

/** Entity → display, given an already-decrypted title. */
internal fun TaskEntity.toUi(displayTitle: String, hasReminder: Boolean) = TaskUi(
    id = id,
    title = displayTitle,
    completed = completed,
    pinned = pinned,
    categoryId = categoryId,
    completedAt = completedAt,
    hasReminder = hasReminder,
    obscured = encrypted && displayTitle == com.leo.checkertic.core.crypto.Vault.LOCKED_PLACEHOLDER
)
