package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a color from Android resources within a @Composable scope.
 * Automatically handles lifecycle via remember and LocalContext.
 */
@Composable
fun AppColor(@ColorRes colorRes: Int): Color =
    remember { LocalContext.current }?.let {
        ContextCompat.getColor(it, colorRes)
    } ?: Color.Unspecified