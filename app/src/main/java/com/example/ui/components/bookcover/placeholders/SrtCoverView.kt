package com.example.ui.components.bookcover.placeholders

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File


@Composable
fun SrtCoverPlaceholder(
    title: String,
    author: String,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Dark slate
            Color(0xFF1E1B4B), // Deep indigo
            Color(0xFF312E81)  // Rich violet
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .padding(6.dp)
    ) {
        // Decorative film strip / subtitle lines canvas at the background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Subtle soundwave/subtitles lines at bottom
            val strokeColor = Color(0xFF6366F1).copy(alpha = 0.15f)
            val lineY1 = h * 0.78f
            val lineY2 = h * 0.86f
            val lineY3 = h * 0.94f
            drawLine(strokeColor, Offset(w * 0.1f, lineY1), Offset(w * 0.9f, lineY1), strokeWidth = 2.dp.toPx())
            drawLine(strokeColor, Offset(w * 0.2f, lineY2), Offset(w * 0.8f, lineY2), strokeWidth = 2.dp.toPx())
            drawLine(strokeColor, Offset(w * 0.15f, lineY3), Offset(w * 0.65f, lineY3), strokeWidth = 2.dp.toPx())
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Format Tag Pill at top
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                    .border(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🎬 СУБТИТРЫ",
                    color = Color(0xFF7DD3FC),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Center Movie / Subtitle Icon & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF818CF8).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f), RoundedCornerShape(19.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Субтитры",
                        tint = Color(0xFFE0E7FF),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title.removeSuffix(".srt").removeSuffix(".vtt").removeSuffix(".SRT"),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }

            // Format footer
            Text(
                text = ".SRT",
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
        }
    }
}