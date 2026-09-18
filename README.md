# Checker-Tic

A notes and tasks app for Android built around a single home-screen widget as the primary daily interface.

## What it does

The **widget** is the main control surface — view tasks by category, check them off, and quick-add new tasks or notes without opening the app. The **full app** handles richer interactions: category management, drag-to-reorder, recurrence configuration, and full note editing.

## Features

**Widget**
- Sidebar toggle between Tasks and Notes views
- Scrollable task list with native checkbox completion (API 31+, icon fallback on older devices)
- Category tabs along the top
- FAB launches a lightweight quick-add dialog directly over the home screen
- Resizable (horizontal and vertical)

**App**
- Bottom navigation: Tasks / Notes / Settings
- Category tabs with inline add
- Staggered-grid note layout with tap-to-edit
- Auto-saving note editor
- Per-category recurrence: once, daily, weekly, monthly, or custom (every N days)
- Nightly widget refresh via WorkManager (enabled by default, togglable)

**Data**
- Room database with four tables: categories, tasks, task_completions (append-only history), notes
- Lazy on-read recurrence reset: checkbox states reset automatically when a new period begins, without depending on a background job for correctness
- Completion history preserved for future analytics (streaks, completion rates)

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI (app) | Jetpack Compose, Material 3 |
| UI (widget) | Jetpack Glance |
| Storage | Room |
| Background | WorkManager |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 37 |

## Device Compatibility

Checker-Tic supports **Android 8.0 (API 26)** and above. On devices running Android 10 or older (API 30 and below), the widget checkbox uses a tap-to-complete icon button instead of a native OS checkbox; behavior is identical.

## Project Structure

```
app/src/main/java/com/leo/checkertic/
  data/
    entity/       -- Room entities (Category, Task, TaskCompletion, Note)
    dao/          -- Room DAOs
    repository/   -- Business logic, recurrence reset
    AppDatabase.kt
  ui/
    theme/        -- Centralized colors, typography, shapes
    screens/      -- Compose screens (Tasks, Notes, NoteEdit, Settings, Main)
    components/   -- Reusable composables (TaskItem, NoteCard)
    viewmodel/    -- ViewModels
    trampoline/   -- QuickAddActivity (widget FAB target)
  widget/
    CheckerTicWidget.kt         -- Glance widget
    CheckerTicWidgetReceiver.kt -- System receiver
  work/
    WidgetRefreshWorker.kt      -- Periodic refresh
  MainActivity.kt
```

## Theming

All colors and typography are centralized in `ui/theme/`. To reskin the app, edit only `Color.kt` and `Type.kt` -- zero hardcoded values exist elsewhere.

## Building

```
./gradlew assembleDebug
```

## License

MIT
