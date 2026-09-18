package com.leo.checkertic.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leo.checkertic.ui.viewmodel.NotesViewModel
import com.leo.checkertic.ui.viewmodel.TasksViewModel

private sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Tasks : Screen("tasks", "Tasks", Icons.Default.CheckCircle)
    data object Notes : Screen("notes", "Notes", Icons.Default.Edit)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavScreens = listOf(Screen.Tasks, Screen.Notes, Screen.Settings)

@Composable
fun MainScreen(
    tasksViewModel: TasksViewModel = viewModel(),
    notesViewModel: NotesViewModel = viewModel()
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            // Hide bottom nav when editing a note
            if (currentRoute != "note_edit/{noteId}") {
                NavigationBar {
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
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Tasks.route) {
                TasksScreen(viewModel = tasksViewModel)
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
                SettingsScreen(viewModel = tasksViewModel)
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
