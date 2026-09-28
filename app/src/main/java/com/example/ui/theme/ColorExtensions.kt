package com.example.ui.theme

import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a color from Android resources within a @Composable scope.
 */
@Composable
fun AppColor(@ColorRes colorRes: Int): Color {
    val context = LocalContext.current
    return Color(ContextCompat.getColor(context, colorRes))
}
