package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class ScreenTab(val route: String, val title: String, val icon: ImageVector) {
    LIBRARY("library", "Библиотека", Icons.Default.LibraryBooks),
    READER("reader", "Читалка", Icons.Default.MenuBook),
    DICTIONARY("dictionary", "Словарь", Icons.Default.Translate),
    SETTINGS("settings", "Настройки", Icons.Default.Settings)
}
