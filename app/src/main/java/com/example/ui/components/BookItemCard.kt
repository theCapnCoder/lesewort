package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Unit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BookUiItem
import com.example.ui.components.BookCoverView
import androidx.compose.foundation.layout.arrange
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.LinearProgressIndicator
import androidx.compose.foundation.layout.CircularProgressIndicator
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Surface

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItemCard(
    item: BookUiItem,
    isSelected: Boolean,
    context: android.content.Context,
    leftBorderColor: androidx.compose.ui.graphics.Color?,
    cardBgColor: androidx.compose.ui.graphics.Color,
    columns: Int = 1,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val book = item.book
    val complexityText = item.stats?.let {
        "Сложность: %.1f%%".format(it.complexityVolumePercent)
    } ?: "Вычисление сложности..."

    val comprehensibilityText = item.stats?.let {
        "Понятно по объему: %.1f%%".format(it.comprehensibleVolumePercent)
    } ?: ""

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_item_${book.id}")
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else cardBgColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (leftBorderColor != null) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(leftBorderColor)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(if (columns > 1) 8.dp else 12.dp)
                    .horizontalArrangement(Arrangement.spacedBy(if (columns > 1) 10.dp else 16.dp))
                    .verticalAlignment(Alignment.Top)
            ) {
                // Left Side: Cover Image with bottom statistics overlays
                val coverWidth = if (columns > 1) 100.dp else 110.dp
                val coverHeight = if (columns > 1) 150.dp else 165.dp
                Column(
                    modifier = Modifier.width(coverWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.ui.components.BookCoverView(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        filePath = book.filePath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(coverHeight)
                    )

                    // Statistics card rendered below the book cover for ALL devices (mobile and tablet)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Blue Circle: count of new words
                                val blueCount = item.newWordsCount
                                val isDoubleBlue = blueCount >= 100
                                val blueText = if (blueCount > 999) "${blueCount / 100 / 10.0}k" else "$blueCount"

                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(
                                            androidx.compose.ui.graphics.Color(0xFF2196F3),
                                            shape = if (isDoubleBlue) RoundedCornerShape(10.dp) else CircleShape
                                        )
                                        .padding(horizontal = if (isDoubleBlue) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = blueText,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }

                                // Yellow Circle: count of learning words
                                val yellowCount = item.learningWordsCount
                                val isDoubleYellow = yellowCount >= 100
                                val yellowText = if (yellowCount > 999) "${yellowCount / 100 / 10.0}k" else "$yellowCount"

                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(
                                            androidx.compose.ui.graphics.Color(0xFFFFC107),
                                            shape = if (isDoubleYellow) RoundedCornerShape(10.dp) else CircleShape
                                        )
                                        .padding(horizontal = if (isDoubleYellow) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = yellowText,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.Black,
                                        maxLines = 1
                                    )
                                }

                                // Green Circle: count of learned words
                                val greenCount = item.learnedWordsCount
                                val isDoubleGreen = greenCount >= 100
                                val greenText = if (greenCount > 999) "${greenCount / 100 / 10.0}k" else "$greenCount"

                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(
                                            androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                            shape = if (isDoubleGreen) RoundedCornerShape(10.dp) else CircleShape
                                        )
                                        .padding(horizontal = if (isDoubleGreen) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = greenText,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }

                            val complexityPercent = item.stats?.complexityVolumePercent ?: 0.0
                            Text(
                                text = "Сложность: %.1f%%".format(complexityPercent),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right Side: Meta information and content description
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = book.title,
                            style = if (columns > 1) androidx.compose.material3.MaterialTheme.typography.titleSmall else androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = book.author,
                            style = if (columns > 1) androidx.compose.material3.MaterialTheme.typography.labelMedium else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val unfamiliarPercent = if (book.uniqueWordsCount > 0) {
                        (item.newWordsCount.toDouble() / book.uniqueWordsCount) * 100.0
                    } else 0.0

                    Text(
                        text = "Незнакомые слова: %.1f%%".format(unfamiliarPercent),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )

                    if (comprehensibilityText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Понимание книги: ${comprehensibilityText.replace("Понятно по объему: ", "")}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Уникальных слов: ${book.uniqueWordsCount}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Всего слов: ${book.totalWords}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val lexicalDensity = if (book.totalWords > 0) {
                        (book.uniqueWordsCount.toDouble() / book.totalWords) * 100.0
                    } else 0.0

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Лексическая плотность: %.1f%%".format(lexicalDensity),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Indicator rows ( swapped order: "Прочитано" then "Глава" )
                    val progress = if (item.totalPages > 0) {
                        item.currentPage.toFloat() / item.totalPages
                    } else 0f
                    val progressPercent = (progress * 100).toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.weight(1f),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            trackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "Прочитано: $progressPercent%",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = if (item.totalChapters > 0) {
                                (book.currentChapterIndex + 1).toFloat() / item.totalChapters
                            } else 0f,
                            modifier = Modifier.weight(1f),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                            trackColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "Страница ${item.currentPage}/${item.totalPages}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}