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
import com.example.ui.components.bookcover.placeholders.SrtCoverPlaceholder
import com.example.ui.components.bookcover.placeholders.TxtCoverPlaceholder
import com.example.ui.components.bookcover.placeholders.StandardBookCoverPlaceholder

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
            .clip(RoundedCornerShape(12.dp))
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
                BookFormatType.SRT -> SrtCoverPlaceholder(title = title, author = author)
                BookFormatType.TXT -> TxtCoverPlaceholder(title = title, author = author)
                BookFormatType.EPUB_OR_BOOK -> StandardBookCoverPlaceholder(title = title, author = author)
            }
        }
    }
}