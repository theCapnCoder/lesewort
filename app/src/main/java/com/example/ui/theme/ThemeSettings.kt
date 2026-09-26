package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object ThemeSettings {
    fun getFontFamily(fontName: String): FontFamily {
        return when (fontName) {
            "serif" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.SansSerif
        }
    }

    // Highlighting colors inside ReaderScreen paragraphs
    fun getLearningHighlightColor(colorName: String): Color {
        return when (colorName) {
            "blue" -> Color(0xFF29B6F6)
            "purple" -> Color(0xFFBA68C8)
            "yellow" -> Color(0xFFFFD54F)
            "orange" -> Color(0xFFFF9100)
            else -> Color(0xFFFF9100) // Default orange
        }
    }

    // Text/badge accent color for learning indicators
    fun getLearningBadgeColor(colorName: String): Color {
        return when (colorName) {
            "blue" -> Color(0xFF0288D1)
            "purple" -> Color(0xFF7B1FA2)
            "yellow" -> Color(0xFFF57F17)
            "orange" -> Color(0xFFE65100)
            else -> Color(0xFFE65100) // Default orange
        }
    }

    // Highlighting colors inside ReaderScreen paragraphs
    fun getLearnedHighlightColor(colorName: String): Color {
        return when (colorName) {
            "teal" -> Color(0xFF4DB6AC)
            "magenta" -> Color(0xFFF06292)
            "gray" -> Color(0xFFB0BEC5)
            else -> Color(0xFF7CA17B) // Default green
        }
    }

    // Text/badge accent color for learned indicators
    fun getLearnedBadgeColor(colorName: String): Color {
        return when (colorName) {
            "teal" -> Color(0xFF00796B)
            "magenta" -> Color(0xFFC2185B)
            "gray" -> Color(0xFF546E7A)
            else -> Color(0xFF2E7D32) // Default green
        }
    }
}
