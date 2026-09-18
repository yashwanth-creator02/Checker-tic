package com.leo.checkertic.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leo.checkertic.ui.theme.ElectricBlue
import com.leo.checkertic.ui.theme.NavyContainer
import com.leo.checkertic.ui.theme.ThemeMode
import com.leo.checkertic.ui.viewmodel.NotesViewModel
import com.leo.checkertic.ui.viewmodel.TasksViewModel

private sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Tasks : Screen("tasks", "Tasks", Icons.Outlined.Checklist)
    data object Notes : Screen("notes", "Notes", Icons.Outlined.Description)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Tune)
}

private val bottomNavScreens = listOf(Screen.Tasks, Screen.Notes, Screen.Settings)

@Composable
fun MainScreen(
    currentThemeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    tasksViewModel: TasksViewModel = viewModel(),
    notesViewModel: NotesViewModel = viewModel(),
    initialTab: String? = null,
    openNoteId: Long? = null
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Handle deep-link navigation from widget or note popup
    LaunchedEffect(openNoteId) {
        if (openNoteId != null && openNoteId > 0) {
            navController.navigate("note_edit/$openNoteId")
        }
    }

    LaunchedEffect(initialTab) {
        if (initialTab == "notes" && openNoteId == null) {
            navController.navigate(Screen.Notes.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val shouldShowBottomBar = currentRoute != "categories_settings" &&
            currentRoute?.startsWith("note_edit") != true

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    bottomNavScreens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
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
                                selectedIconColor = ElectricBlue,
                                selectedTextColor = ElectricBlue,
                                indicatorColor = NavyContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Tasks.route) {
                TasksScreen(
                    viewModel = tasksViewModel,
                    onOpenSettings = {
                        navController.navigate("categories_settings")
                    }
                )
            }
            composable(Screen.Notes.route) {
                NotesScreen(
                    viewModel = notesViewModel,
                    onNoteClick = { noteId ->
                        navController.navigate("note_edit/$noteId")
                    }
                )
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
