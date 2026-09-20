package com.leo.checkertic.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.InsertChartOutlined
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassBottomBoundary
import com.leo.checkertic.ui.theme.ThemeMode
import com.leo.checkertic.ui.viewmodel.AnalyticsViewModel
import com.leo.checkertic.ui.viewmodel.NotesViewModel
import com.leo.checkertic.ui.viewmodel.TasksViewModel

private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Tasks : Screen("tasks", "Tasks", Icons.Outlined.Checklist)
    data object Notes : Screen("notes", "Notes", Icons.Outlined.Description)
    data object Analytics : Screen("analytics", "Stats", Icons.Outlined.InsertChartOutlined)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Tune)
}

private val bottomNavScreens = listOf(
    Screen.Tasks,
    Screen.Notes,
    Screen.Analytics,
    Screen.Settings
)

/**
 * Navigation host.
 *
 * Analytics joins as a peer tab (feature 1). Its ViewModel is created here
 * rather than inside the screen so it survives tab switches — the aggregation
 * it does is cheap but not free, and recomputing a year of heatmap every time
 * the user glances at Notes and comes back would be visible.
 *
 * The bottom bar is the third sanctioned glass surface: a boundary the
 * content scrolls under.
 */
@Composable
fun MainScreen(
    currentThemeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    tasksViewModel: TasksViewModel = viewModel(),
    notesViewModel: NotesViewModel = viewModel(),
    analyticsViewModel: AnalyticsViewModel = viewModel(),
    initialTab: String? = null,
    openNoteId: Long? = null,
    openCategoryId: Long? = null
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val colors = AppTheme.colors
    val motion = AppTheme.motion

    LaunchedEffect(openNoteId) {
        if (openNoteId != null && openNoteId > 0) {
            navController.navigate("note_edit/$openNoteId")
        }
    }

    // Reminder deep link: select the category the notification was about,
    // then land on Tasks. Selecting first means the list is already correct
    // when the screen appears rather than flashing the previous category.
    LaunchedEffect(openCategoryId) {
        if (openCategoryId != null && openCategoryId > 0) {
            tasksViewModel.selectCategory(openCategoryId)
        }
    }

    LaunchedEffect(initialTab) {
        val target = when (initialTab) {
            "notes" -> Screen.Notes.route
            "analytics" -> Screen.Analytics.route
            "tasks" -> Screen.Tasks.route
            else -> null
        }
        if (target != null && openNoteId == null) {
            navController.navigate(target) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val showBottomBar = currentRoute != "categories_settings" &&
        currentRoute?.startsWith("note_edit") != true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                GlassBottomBoundary {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        contentColor = colors.textPrimary
                    ) {
                        bottomNavScreens.forEach { screen ->
                            NavigationBarItem(
                                selected = currentRoute == screen.route,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.label
                                    )
                                },
                                label = { Text(screen.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.accent,
                                    selectedTextColor = colors.accent,
                                    indicatorColor = colors.accentContainer,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = colors.root
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier.padding(innerPadding),
            // A cross-fade rather than a slide. A slide transition has to
            // rasterise both screens into layers and move them; on the
            // analytics tab that means compositing a full screen of charts
            // twice per frame for the duration of the transition.
            enterTransition = { fadeIn(tween(motion.normal, easing = motion.standard)) },
            exitTransition = { fadeOut(tween(motion.normal, easing = motion.standard)) },
            popEnterTransition = { fadeIn(tween(motion.normal, easing = motion.standard)) },
            popExitTransition = { fadeOut(tween(motion.normal, easing = motion.standard)) }
        ) {
            composable(Screen.Tasks.route) {
                TasksScreen(
                    viewModel = tasksViewModel,
                    onOpenSettings = { navController.navigate("categories_settings") }
                )
            }
            composable(Screen.Notes.route) {
                NotesScreen(
                    viewModel = notesViewModel,
                    onNoteClick = { noteId -> navController.navigate("note_edit/$noteId") }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(viewModel = analyticsViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = tasksViewModel,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
            composable("categories_settings") {
                CategoriesSettingsScreen(
                    viewModel = tasksViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "note_edit/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong("noteId") ?: return@composable
                NoteEditScreen(
                    noteId = noteId,
                    viewModel = notesViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
