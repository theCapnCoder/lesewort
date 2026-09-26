package com.example.ui.components

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

enum class BookFormatType {
    SRT,
    TXT,
    EPUB_OR_BOOK
}

fun detectBookFormat(title: String, author: String, filePath: String? = null): BookFormatType {
    val cleanTitle = title.lowercase().trim()
    val cleanAuthor = author.lowercase().trim()
    val cleanPath = (filePath ?: "").lowercase().trim()
    return when {
        cleanTitle.endsWith(".srt") || cleanTitle.endsWith(".vtt") ||
        cleanPath.endsWith(".srt") || cleanPath.endsWith(".vtt") ||
        cleanAuthor.contains("субтитр") || cleanAuthor.contains("subtitle") -> BookFormatType.SRT

        cleanTitle.endsWith(".txt") || cleanPath.endsWith(".txt") ||
        cleanAuthor == "текст" || cleanAuthor == "документ" || cleanAuthor == "txt" -> BookFormatType.TXT

        else -> BookFormatType.EPUB_OR_BOOK
    }
}

@Composable
fun BookCoverView(
    bookId: Int,
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    filePath: String? = null
) {
    val context = LocalContext.current
    val format = remember(title, author, filePath) {
        detectBookFormat(title, author, filePath)
    }

    // Only load bitmap if format is regular book/epub, NEVER for subtitles or plain text
    val coverBitmap = remember(bookId, format) {
        if (format == BookFormatType.EPUB_OR_BOOK) {
            val file = File(context.filesDir, "covers/cover_${bookId}.jpg")
            if (file.exists()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)
                } catch (e: Exception) {
                    null
                }
            } else null
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = "Обложка книги: $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            when (format) {
                BookFormatType.SRT -> {
                    SrtCoverPlaceholder(title = title, author = author)
                }
                BookFormatType.TXT -> {
                    TxtCoverPlaceholder(title = title, author = author)
                }
                BookFormatType.EPUB_OR_BOOK -> {
                    StandardBookCoverPlaceholder(title = title, author = author)
                }
            }
        }
    }
}

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

@Composable
fun TxtCoverPlaceholder(
    title: String,
    author: String,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF042F2E), // Deep teal
            Color(0xFF065F46), // Forest emerald
            Color(0xFF0F766E)  // Rich cyan
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .padding(6.dp)
    ) {
        // Decorative document lines canvas at background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strokeColor = Color(0xFF34D399).copy(alpha = 0.15f)
            val lineY1 = h * 0.78f
            val lineY2 = h * 0.86f
            val lineY3 = h * 0.94f
            drawLine(strokeColor, Offset(w * 0.15f, lineY1), Offset(w * 0.85f, lineY1), strokeWidth = 2.dp.toPx())
            drawLine(strokeColor, Offset(w * 0.15f, lineY2), Offset(w * 0.75f, lineY2), strokeWidth = 2.dp.toPx())
            drawLine(strokeColor, Offset(w * 0.15f, lineY3), Offset(w * 0.55f, lineY3), strokeWidth = 2.dp.toPx())
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
                    .background(Color(0xFF34D399).copy(alpha = 0.2f))
                    .border(0.5.dp, Color(0xFF34D399).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "📄 ТЕКСТ",
                    color = Color(0xFFA7F3D0),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Center Document Icon & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(19.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Текст",
                        tint = Color(0xFFECFDF5),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title.removeSuffix(".txt").removeSuffix(".TXT"),
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
                text = ".TXT",
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6EE7B7)
            )
        }
    }
}

@Composable
fun StandardBookCoverPlaceholder(
    title: String,
    author: String,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.secondaryContainer
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Format Tag Pill at top
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "📖 КНИГА",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Center Book Icon & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (author.isNotBlank() && author != "Unknown Author") {
                Text(
                    text = author,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
