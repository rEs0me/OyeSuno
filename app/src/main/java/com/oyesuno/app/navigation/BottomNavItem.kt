package com.oyesuno.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation items — 4 regular tabs with a center FAB for Record.
 * Layout: [Home] [Library] [⏺ Record FAB] [Triggers] [Settings]
 */
enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    LIBRARY(Routes.LIBRARY, "Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
    // Center slot is reserved for the Record FAB — not a nav item
    TRIGGERS(Routes.TRIGGERS, "Triggers", Icons.Outlined.Tune, Icons.Filled.Tune),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}
