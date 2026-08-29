package com.oyesuno.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oyesuno.feature.editor.EditorScreen
import com.oyesuno.feature.home.HomeScreen
import com.oyesuno.feature.library.LibraryScreen
import com.oyesuno.feature.player.PlayerScreen
import com.oyesuno.feature.recorder.RecorderScreen
import com.oyesuno.feature.settings.SettingsScreen

@Composable
fun OyeSunoApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on main tabs (not on recorder/player/editor)
    val showBottomBar = currentRoute in BottomNavItem.entries.map { it.route }
    val isRecording = currentRoute == Routes.RECORDER

    val isRecordingActive by com.oyesuno.core.audio.recorder.RecordingEngineImpl
        .isRecordingActiveFlow
        .collectAsState()

    LaunchedEffect(isRecordingActive, currentRoute) {
        if (isRecordingActive && currentRoute != Routes.RECORDER && currentRoute != null) {
            navController.navigate(Routes.RECORDER) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                OyeSunoBottomBar(
                    navController = navController,
                    currentRoute = currentRoute,
                    isRecording = isRecordingActive,
                    onRecordClick = { navController.navigate(Routes.RECORDER) }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onStartRecording = { navController.navigate(Routes.RECORDER) },
                    onRecordingClick = { id -> navController.navigate(Routes.playerRoute(id)) },
                    onNavigateToTriggers = { navController.navigate(Routes.TRIGGERS) }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onRecordingClick = { id -> navController.navigate(Routes.playerRoute(id)) }
                )
            }
            composable(Routes.TRIGGERS) {
                TriggersScreen(
                    onStartRecording = { navController.navigate(Routes.RECORDER) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.RECORDER) {
                RecorderScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("recordingId") { type = NavType.LongType })
            ) { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getLong("recordingId") ?: return@composable
                PlayerScreen(
                    recordingId = recordingId,
                    onNavigateBack = { navController.popBackStack() },
                    onEditClick = { navController.navigate(Routes.editorRoute(recordingId)) }
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("recordingId") { type = NavType.LongType })
            ) { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getLong("recordingId") ?: return@composable
                EditorScreen(
                    recordingId = recordingId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Bottom navigation bar with elevated center Record FAB.
 * Layout: [Home] [Library] [🎙 Record] [Triggers] [Settings]
 */
@Composable
private fun OyeSunoBottomBar(
    navController: NavController,
    currentRoute: String?,
    isRecording: Boolean,
    onRecordClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Navigation bar
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            tonalElevation = 2.dp
        ) {
            val items = BottomNavItem.entries.toList()
            // Left side items (Home, Library)
            items.take(2).forEach { item ->
                NavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == item.route) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                )
            }

            // Center spacer for the FAB
            NavigationBarItem(
                selected = false,
                onClick = { },
                enabled = false,
                icon = { Box(modifier = Modifier.size(48.dp)) },
                label = { Text("Record", style = MaterialTheme.typography.labelSmall) }
            )

            // Right side items (Triggers, Settings)
            items.drop(2).forEach { item ->
                NavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == item.route) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Elevated Record FAB — centered, raised above the bar
        val fabScale by animateFloatAsState(
            targetValue = if (isRecording) 1.1f else 1f,
            animationSpec = spring(),
            label = "fab_scale"
        )
        val fabColor by animateColorAsState(
            targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            label = "fab_color"
        )

        FloatingActionButton(
            onClick = onRecordClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-16).dp)
                .size(56.dp)
                .scale(fabScale),
            shape = CircleShape,
            containerColor = fabColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
