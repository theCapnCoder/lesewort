package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ThemeSettings
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import com.example.ui.DEFAULT_GERMAN_PROMPT
import com.example.ui.DEFAULT_ENGLISH_PROMPT
import com.example.ui.DEFAULT_FRENCH_PROMPT
import com.example.ui.CustomPrompt
import com.example.ui.GroqApiKeyItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiKey by viewModel.groqApiKey.collectAsState()
    val groqApiKeys by viewModel.groqApiKeys.collectAsState()
    val selectedGroqKeyId by viewModel.selectedGroqKeyId.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKeyItem by remember { mutableStateOf<GroqApiKeyItem?>(null) }
    var keyToDelete by remember { mutableStateOf<GroqApiKeyItem?>(null) }

    var showPromptManagement by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showPromptManagement) {
            showPromptManagement = false
        } else {
            onNavigateToTab(ScreenTab.LIBRARY)
        }
    }
    val currentLanguage by viewModel.currentLanguage.collectAsState()

    var inputKey by remember(apiKey) { mutableStateOf(apiKey) }
    var passwordVisible by remember { mutableStateOf(false) }

    if (showPromptManagement) {
        val customPrompts by viewModel.customPrompts.collectAsState()
        var editingPromptId by remember { mutableStateOf<String?>(null) }
        var editName by remember { mutableStateOf("") }
        var editGerman by remember { mutableStateOf("") }
        var editEnglish by remember { mutableStateOf("") }
        var editFrench by remember { mutableStateOf("") }

        var isCreatingNew by remember { mutableStateOf(false) }
        var newPromptName by remember { mutableStateOf("") }
        var newPromptGerman by remember { mutableStateOf("") }
        var newPromptEnglish by remember { mutableStateOf("") }
        var newPromptFrench by remember { mutableStateOf("") }
        var isDeleteModeEnabled by remember { mutableStateOf(false) }

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                IconButton(onClick = { showPromptManagement = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Промпты ИИ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { isDeleteModeEnabled = !isDeleteModeEnabled }
                ) {
                    Icon(
                        imageVector = if (isDeleteModeEnabled) Icons.Default.Close else Icons.Default.Delete,
                        contentDescription = if (isDeleteModeEnabled) "Выйти из режима удаления" else "Режим удаления промптов",
                        tint = if (isDeleteModeEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Создавайте и настраивайте свои шаблоны запросов для перевода немецких и английских слов. Выбранный промпт будет применяться при запросах к Groq.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Prompts List
            customPrompts.forEach { prompt ->
                val isActive = prompt.isActive
                val isEditing = editingPromptId == prompt.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = {
                                        viewModel.activateCustomPrompt(context, prompt.id)
                                        Toast.makeText(context, "Активирован промпт: ${prompt.name}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = prompt.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        if (isEditing) {
                                            editingPromptId = null
                                        } else {
                                            editingPromptId = prompt.id
                                            editName = prompt.name
                                            editGerman = prompt.germanPromptTemplate
                                            editEnglish = prompt.englishPromptTemplate
                                            editFrench = prompt.frenchPromptTemplate
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                        contentDescription = "Редактировать"
                                    )
                                }

                                val systemPromptIds = listOf("default", "classic", "etymology_breakdown", "mnemonics_associations", "nuances_facts")
                                if (isDeleteModeEnabled && !systemPromptIds.contains(prompt.id)) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteCustomPrompt(context, prompt.id)
                                            Toast.makeText(context, "Промпт удален", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (isEditing) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Название шаблона") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Немецкий шаблон (DE):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = editGerman,
                                onValueChange = { editGerman = it },
                                placeholder = { Text("Используйте {word} и {sentence} как плейсхолдеры") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Английский шаблон (EN):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = editEnglish,
                                onValueChange = { editEnglish = it },
                                placeholder = { Text("Используйте {word} и {sentence} как плейсхолдеры") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Французский шаблон (FR):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = editFrench,
                                onValueChange = { editFrench = it },
                                placeholder = { Text("Используйте {word} и {sentence} как плейсхолдеры") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateCustomPrompt(context, prompt.id, editName, editGerman, editEnglish, editFrench)
                                        editingPromptId = null
                                        Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Сохранить")
                                }

                                OutlinedButton(
                                    onClick = {
                                        editGerman = DEFAULT_GERMAN_PROMPT
                                        editEnglish = DEFAULT_ENGLISH_PROMPT
                                        editFrench = DEFAULT_FRENCH_PROMPT
                                        Toast.makeText(context, "Шаблон сброшен к исходному", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Сбросить")
                                }
                            }
                        }
                    }
                }
            }

            // Create New Prompt Section
            if (isCreatingNew) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Новый шаблон промпта",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = newPromptName,
                            onValueChange = { newPromptName = it },
                            label = { Text("Название шаблона") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Немецкий шаблон (DE):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = newPromptGerman,
                            onValueChange = { newPromptGerman = it },
                            placeholder = { Text("Введите промпт с {word} и {sentence}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Английский шаблон (EN):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = newPromptEnglish,
                            onValueChange = { newPromptEnglish = it },
                            placeholder = { Text("Введите промпт с {word} и {sentence}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Французский шаблон (FR):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = newPromptFrench,
                            onValueChange = { newPromptFrench = it },
                            placeholder = { Text("Введите промпт с {word} и {sentence}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val nameTrim = newPromptName.trim()
                                    if (nameTrim.isNotEmpty()) {
                                        viewModel.addCustomPrompt(
                                            context,
                                            nameTrim,
                                            newPromptGerman.ifBlank { DEFAULT_GERMAN_PROMPT },
                                            newPromptEnglish.ifBlank { DEFAULT_ENGLISH_PROMPT },
                                            newPromptFrench.ifBlank { DEFAULT_FRENCH_PROMPT }
                                        )
                                        isCreatingNew = false
                                        newPromptName = ""
                                        newPromptGerman = ""
                                        newPromptEnglish = ""
                                        newPromptFrench = ""
                                        Toast.makeText(context, "Шаблон создан", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Введите название шаблона", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Создать")
                            }

                            OutlinedButton(
                                onClick = {
                                    isCreatingNew = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Отмена")
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        isCreatingNew = true
                        newPromptName = ""
                        newPromptGerman = DEFAULT_GERMAN_PROMPT
                        newPromptEnglish = DEFAULT_ENGLISH_PROMPT
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать свой промпт")
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Spacious Display Heading
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // API Configuration Card
            // Groq API Integration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Groq API Ключи",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = "${groqApiKeys.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Выберите активный ключ для работы словаря и AI-функций.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Keys list
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        groqApiKeys.forEach { keyItem ->
                            val isSelected = keyItem.id == selectedGroqKeyId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.selectGroqApiKey(context, keyItem.id)
                                        Toast.makeText(context, "Выбран ключ: «${keyItem.name}»", Toast.LENGTH_SHORT).show()
                                    }
                                    .testTag("groq_key_item_${keyItem.id}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.selectGroqApiKey(context, keyItem.id)
                                            Toast.makeText(context, "Выбран ключ: «${keyItem.name}»", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = keyItem.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = keyItem.maskedKey,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { editingKeyItem = keyItem },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Редактировать ключ",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    if (groqApiKeys.size > 1) {
                                        IconButton(
                                            onClick = { keyToDelete = keyItem },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Удалить ключ",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Add new key button
                    OutlinedButton(
                        onClick = { showAddKeyDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("add_groq_api_key_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Добавить ключ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // AI Prompt Management Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Промпты и запросы к ИИ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Настройте системные инструкции (промпты), которые отправляются ИИ для получения переводов и грамматических разборов.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = { showPromptManagement = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Управление промптами ИИ")
                    }
                }
            }

            // Target Correct Count Preferences Card
            val targetCorrectCount by viewModel.targetCorrectCount.collectAsState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Порог изучения слов",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Количество правильных ответов подряд в игре «Найди пару», после которого слово считается выученным полностью и удаляется из изучаемых.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (targetCorrectCount > 3) {
                                    viewModel.setTargetCorrectCount(context, targetCorrectCount - 1)
                                }
                            },
                            enabled = targetCorrectCount > 3
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Уменьшить")
                        }

                        Text(
                            text = "$targetCorrectCount раз",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        IconButton(
                            onClick = {
                                if (targetCorrectCount < 25) {
                                    viewModel.setTargetCorrectCount(context, targetCorrectCount + 1)
                                }
                            },
                            enabled = targetCorrectCount < 25
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Увеличить")
                        }
                    }
                }
            }

            // AI Models Preference Card
            val availableModels by viewModel.availableModels.collectAsState()
            val selectedModel by viewModel.selectedModel.collectAsState()
            var newModelName by remember { mutableStateOf("") }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Модели Groq AI и резервирование",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Выберите предпочтительную модель. Если выбранная модель вернет ошибку, приложение автоматически переключится на следующую модель из списка.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // List of available models
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        availableModels.forEach { modelName ->
                            val isSelected = modelName == selectedModel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                    .clickable {
                                        viewModel.setSelectedModel(context, modelName)
                                        Toast.makeText(context, "Выбрана модель: $modelName", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setSelectedModel(context, modelName)
                                            Toast.makeText(context, "Выбрана модель: $modelName", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val cleanId = modelName.substringBefore(" (").trim()
                                    val tokenSuffix = when (cleanId) {
                                        "openai/gpt-oss-20b" -> " (~300 000 – 500 000 токенов/день)"
                                        "openai/gpt-oss-120b" -> " (~200 000 токенов/день)"
                                        "qwen/qwen3.8-27b" -> " (~500 000 токенов/день)"
                                        "groq/compound-mini" -> " (~500 000 токенов/день)"
                                        "openai/gpt-oss-safeguard-20b" -> " (~300 000 – 500 000 токенов/день)"
                                        else -> ""
                                    }
                                    val displayName = if (modelName.contains("токенов")) modelName else "$modelName$tokenSuffix"

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        // Add small descriptions for default models
                                        val description = when (cleanId) {
                                            "openai/gpt-oss-20b" -> "20B • ~1 000 – 1 500 запросов/день • Быстрая и экономная"
                                            "openai/gpt-oss-120b" -> "120B • ~1 000 запросов/день • Флагманская модель"
                                            "qwen/qwen3.8-27b" -> "27B • ~1 500 – 1 600 запросов/день • Высокая точность рассуждений"
                                            "groq/compound-mini" -> "Compound Mini • ~1 000 – 1 500 запросов/день • Высокая скорость"
                                            "openai/gpt-oss-safeguard-20b" -> "20B • ~1 000 – 1 500 запросов/день • Безопасные генерации"
                                            else -> "Пользовательская модель"
                                        }
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = {
                                        if (availableModels.size <= 1) {
                                            Toast.makeText(context, "Нельзя удалить последнюю модель в списке", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.removeModel(context, modelName)
                                            Toast.makeText(context, "Модель удалена: $modelName", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить модель",
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Add a new model form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newModelName,
                            onValueChange = { newModelName = it },
                            label = { Text("Имя новой модели") },
                            placeholder = { Text("например, qwen/qwen3.8-27b") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = {
                                val trimmed = newModelName.trim()
                                if (trimmed.isNotEmpty()) {
                                    if (availableModels.contains(trimmed)) {
                                        Toast.makeText(context, "Модель уже добавлена", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.addModel(context, trimmed)
                                        Toast.makeText(context, "Добавлена новая модель: $trimmed", Toast.LENGTH_SHORT).show()
                                        newModelName = ""
                                    }
                                } else {
                                    Toast.makeText(context, "Введите имя модели", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить")
                        }
                    }
                }
            }

            // Language Preferences Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Язык книги по умолчанию",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Укажите язык читаемой литературы, чтобы ИИ переводил и объяснял слова с учетом особенностей соответствующей грамматики.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val languages = listOf("de" to "Немецкий (DE)", "en" to "Английский (EN)", "fr" to "Французский (FR)")
                        languages.forEach { (code, name) ->
                            val isSelected = currentLanguage == code
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setCurrentLanguage(context, code)
                                    Toast.makeText(context, "Язык изменен на $name", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lang_chip_$code")
                            )
                        }
                    }
                }
            }

            // Theme Preferences Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Тема оформления",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Выберите желаемую цветовую схему для комфортного чтения в любое время суток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            "system" to "Система",
                            "light" to "Светлая",
                            "dark" to "Темная"
                        )
                        val currentThemeMode by viewModel.appThemeMode.collectAsState()
                        themes.forEach { (code, name) ->
                            val isSelected = currentThemeMode == code
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setAppThemeMode(context, code)
                                },
                                label = { 
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ) 
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_chip_$code")
                            )
                        }
                    }
                }
            }

            // Font Size Preferences Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Размер шрифта для чтения",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Настройте размер текста в режиме чтения для комфортного восприятия.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val currentFontSize by viewModel.readingFontSize.collectAsState()
                        IconButton(
                            onClick = {
                                if (currentFontSize > 12f) {
                                    viewModel.setReadingFontSize(context, currentFontSize - 1f)
                                }
                            },
                            enabled = currentFontSize > 12f,
                            modifier = Modifier.testTag("font_size_minus")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Уменьшить")
                        }
                        
                        Text(
                            text = "${currentFontSize.toInt()} sp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .testTag("font_size_value")
                        )

                        IconButton(
                            onClick = {
                                if (currentFontSize < 32f) {
                                    viewModel.setReadingFontSize(context, currentFontSize + 1f)
                                }
                            },
                            enabled = currentFontSize < 32f,
                            modifier = Modifier.testTag("font_size_plus")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Увеличить")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val currentFontSize by viewModel.readingFontSize.collectAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пример текста для чтения",
                            fontSize = currentFontSize.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Размер шрифта для перевода",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Настройте размер текста во всплывающем окне перевода слова.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val currentTranslationFontSize by viewModel.translationFontSize.collectAsState()
                        IconButton(
                            onClick = {
                                if (currentTranslationFontSize > 10f) {
                                    viewModel.setTranslationFontSize(context, currentTranslationFontSize - 1f)
                                }
                            },
                            enabled = currentTranslationFontSize > 10f,
                            modifier = Modifier.testTag("translation_font_size_minus")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Уменьшить")
                        }
                        
                        Text(
                            text = "${currentTranslationFontSize.toInt()} sp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .testTag("translation_font_size_value")
                        )

                        IconButton(
                            onClick = {
                                if (currentTranslationFontSize < 28f) {
                                    viewModel.setTranslationFontSize(context, currentTranslationFontSize + 1f)
                                }
                            },
                            enabled = currentTranslationFontSize < 28f,
                            modifier = Modifier.testTag("translation_font_size_plus")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Увеличить")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val currentTranslationFontSize by viewModel.translationFontSize.collectAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пример текста объяснения слова",
                            fontSize = currentTranslationFontSize.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Word Status Color Customization Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatColorFill,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Цвета статуса слов",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Настройте цвета подсветки слов на изучении и выученных слов в книге.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Subtitle 1: Изучаю (Learning)
                    Text(
                        text = "Статус «Изучаю»:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val learningOptionColors = listOf(
                        "orange" to Color(0xFFFF9100),
                        "blue" to Color(0xFF29B6F6),
                        "purple" to Color(0xFFBA68C8),
                        "yellow" to Color(0xFFFFD54F)
                    )
                    val currentLearningColor by viewModel.learningColor.collectAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        learningOptionColors.forEach { (code, composeColor) ->
                            val isSelected = currentLearningColor == code
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setLearningColor(context, code)
                                    }
                                    .testTag("color_learning_$code"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбрано",
                                        tint = if (code == "yellow") Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Subtitle 2: Выучено (Learned)
                    Text(
                        text = "Статус «Выучено»:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val learnedOptionColors = listOf(
                        "green" to Color(0xFF7CA17B),
                        "teal" to Color(0xFF4DB6AC),
                        "magenta" to Color(0xFFF06292),
                        "gray" to Color(0xFFB0BEC5)
                    )
                    val currentLearnedColor by viewModel.learnedColor.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        learnedOptionColors.forEach { (code, composeColor) ->
                            val isSelected = currentLearnedColor == code
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setLearnedColor(context, code)
                                    }
                                    .testTag("color_learned_$code"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбрано",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Help Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Где взять API ключ?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "1. Зарегистрируйтесь на console.groq.com\n" +
                                "2. Перейдите во вкладку «API Keys» и нажмите «Create API Key»\n" +
                                "3. Скопируйте созданный ключ и добавьте его в список выше.\n" +
                                "Запросы происходят напрямую через безопасный защищенный клиент на вашем устройстве.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Add Groq Key Dialog
    if (showAddKeyDialog) {
        var newName by remember { mutableStateOf("") }
        var newKey by remember { mutableStateOf("") }
        var makeActive by remember { mutableStateOf(true) }
        var isKeyVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Добавить Groq API ключ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Название ключа") },
                        placeholder = { Text("Например: Ключ 2, Резервный, Домашний") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("API ключ (gsk_...)") },
                        placeholder = { Text("gsk_...") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { clipText ->
                                        if (clipText.isNotBlank()) {
                                            newKey = clipText.trim()
                                            Toast.makeText(context, "Ключ вставлен из буфера", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Вставить из буфера", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isKeyVisible) "Скрыть" else "Показать"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { makeActive = !makeActive }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = makeActive,
                            onCheckedChange = { makeActive = it }
                        )
                        Text(
                            text = "Сделать этот ключ активным сразу",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedKey = newKey.trim()
                        if (trimmedKey.isNotBlank()) {
                            viewModel.addGroqApiKey(
                                context = context,
                                name = newName.trim(),
                                key = trimmedKey,
                                selectImmediately = makeActive
                            )
                            showAddKeyDialog = false
                            Toast.makeText(context, "API ключ успешно добавлен в память!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Пожалуйста, введите API ключ", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = newKey.trim().isNotBlank()
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Edit Groq Key Dialog
    editingKeyItem?.let { currentItem ->
        var editName by remember(currentItem) { mutableStateOf(currentItem.name) }
        var editKey by remember(currentItem) { mutableStateOf(currentItem.key) }
        var isKeyVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingKeyItem = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Редактировать ключ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Название ключа") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editKey,
                        onValueChange = { editKey = it },
                        label = { Text("API ключ (gsk_...)") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { clipText ->
                                        if (clipText.isNotBlank()) {
                                            editKey = clipText.trim()
                                            Toast.makeText(context, "Ключ вставлен из буфера", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Вставить из буфера", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isKeyVisible) "Скрыть" else "Показать"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedKey = editKey.trim()
                        if (trimmedKey.isNotBlank()) {
                            viewModel.updateGroqApiKey(
                                context = context,
                                id = currentItem.id,
                                name = editName.trim(),
                                key = trimmedKey
                            )
                            editingKeyItem = null
                            Toast.makeText(context, "Ключ обновлен в памяти устройства", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ключ не может быть пустым", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = editKey.trim().isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingKeyItem = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Delete Key Confirmation Dialog
    keyToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text("Удалить ключ?") },
            text = { Text("Вы уверены, что хотите удалить ключ «${item.name}» (${item.maskedKey}) из памяти устройства?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroqApiKey(context, item.id)
                        keyToDelete = null
                        Toast.makeText(context, "Ключ удален из памяти", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
