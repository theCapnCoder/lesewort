package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AppLanguage(
    val code: String,
    val nameRu: String,
    val nativeName: String,
    val flagEmoji: String
)

val SUPPORTED_LANGUAGES = listOf(
    AppLanguage("de", "Немецкий", "Deutsch", "🇩🇪"),
    AppLanguage("en", "Английский", "English", "🇬🇧"),
    AppLanguage("fr", "Французский", "Français", "🇫🇷"),
    AppLanguage("es", "Испанский", "Español", "🇪🇸"),
    AppLanguage("it", "Итальянский", "Italiano", "🇮🇹"),
    AppLanguage("ru", "Русский", "Русский", "🇷🇺")
)

fun getLanguageNameRu(code: String): String {
    return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.nameRu
        ?: when (code.lowercase()) {
            "de" -> "Немецкий"
            "en" -> "Английский"
            "fr" -> "Французский"
            "es" -> "Испанский"
            "it" -> "Итальянский"
            "ru" -> "Русский"
            else -> code.uppercase()
        }
}

@Composable
fun LanguageFlagIcon(
    languageCode: String,
    modifier: Modifier = Modifier
) {
    when (languageCode.lowercase()) {
        "de" -> GermanFlagIcon(modifier)
        "fr" -> FrenchFlagIcon(modifier)
        "en" -> EnglishFlagIcon(modifier)
        "es" -> SpanishFlagIcon(modifier)
        "it" -> ItalianFlagIcon(modifier)
        "ru" -> RussianFlagIcon(modifier)
        else -> GermanFlagIcon(modifier)
    }
}

@Composable
fun GermanFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF000000)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFF0000)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFFCC00)))
        }
    }
}

@Composable
fun EnglishFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // 1. Blue background
            drawRect(color = Color(0xFF00247D))
            
            // 2. White diagonals
            val diagStroke = h * 0.15f
            val redDiagStroke = h * 0.06f
            
            drawLine(Color.White, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = diagStroke)
            drawLine(Color.White, start = Offset(0f, h), end = Offset(w, 0f), strokeWidth = diagStroke)
            
            // 3. Red diagonals
            drawLine(Color(0xFFCF142B), start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = redDiagStroke)
            drawLine(Color(0xFFCF142B), start = Offset(0f, h), end = Offset(w, 0f), strokeWidth = redDiagStroke)
            
            // 4. White cross
            val crossWidth = w * 0.22f
            drawRect(Color.White, topLeft = Offset((w - crossWidth) / 2f, 0f), size = Size(crossWidth, h))
            drawRect(Color.White, topLeft = Offset(0f, (h - crossWidth * (h/w)) / 2f), size = Size(w, crossWidth * (h/w)))
            
            // 5. Red cross
            val redCrossWidth = w * 0.12f
            drawRect(Color(0xFFCF142B), topLeft = Offset((w - redCrossWidth) / 2f, 0f), size = Size(redCrossWidth, h))
            drawRect(Color(0xFFCF142B), topLeft = Offset(0f, (h - redCrossWidth * (h/w)) / 2f), size = Size(w, redCrossWidth * (h/w)))
        }
    }
}

@Composable
fun FrenchFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF002395)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFFFFF)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFED2939)))
        }
    }
}

@Composable
fun SpanishFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFAA151B)))
            Box(modifier = Modifier.weight(2f).fillMaxWidth().background(Color(0xFFF1BF00)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFAA151B)))
        }
    }
}

@Composable
fun ItalianFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF009246)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFFFFF)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFCE2B37)))
        }
    }
}

@Composable
fun RussianFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFFFFFF)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0039A6)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFD52B1E)))
        }
    }
}
