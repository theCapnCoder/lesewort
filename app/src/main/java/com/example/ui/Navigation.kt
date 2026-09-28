package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class ScreenTab(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector = icon
) {
    LIBRARY("library", "Библиотека", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark),
    READER("reader", "Читалка", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    DICTIONARY("dictionary", "Словарь", Icons.Filled.Translate, Icons.Outlined.Translate),
    SETTINGS("settings", "Настройки", Icons.Filled.Settings, Icons.Outlined.Settings)
}
