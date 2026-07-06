package com.huilian.comfymobile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem(
        "home", "首页",
        Icons.Filled.Home, Icons.Outlined.Home
    )
    object Workflows : BottomNavItem(
        "workflows", "工作流",
        Icons.Filled.Build, Icons.Outlined.Build
    )
    object Gallery : BottomNavItem(
        "gallery", "图库",
        Icons.Filled.Image, Icons.Outlined.Image
    )
    object Queue : BottomNavItem(
        "queue", "队列",
        Icons.Filled.List, Icons.Outlined.List
    )
    object Settings : BottomNavItem(
        "settings", "设置",
        Icons.Filled.Settings, Icons.Outlined.Settings
    )
}
