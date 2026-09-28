package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.data.db.AppDatabase
import com.example.data.repository.BookRepository
import com.example.ui.*
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { 
        BookRepository(
            database.knownWordDao(), 
            database.bookDao(), 
            database.chapterDao(),
            database.studyWordDao(),
            database.wordActivityDao()
        ) 
    }
    
    private val viewModel: MainViewModel by viewModels { 
        MainViewModelFactory(repository) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation strictly to prevent exiting reader mode on phone rotation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        // Initialize preferences settings
        viewModel.initSettings(applicationContext)

        // One-time cleanup for any historical dictionary words that were incorrectly seeded as "today's" activity
        lifecycleScope.launch {
            repository.clearSeededEventsOnceIfNeeded(applicationContext)
        }
        
        // Install default German book on fresh installation
        lifecycleScope.launch {
            com.example.data.engine.DefaultBookGenerator.installDefaultBookIfNeeded(
                applicationContext,
                viewModel,
                repository
            )
        }

        // Handle external file opening (e.g., SRT, TXT, EPUB)
        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.appThemeMode.collectAsState()
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                var currentTab by rememberSaveable { mutableStateOf(ScreenTab.LIBRARY) }
                val storyGenState by viewModel.storyGenerationState.collectAsState()
                val isGenerateStoryActive by viewModel.isGenerateStoryActive.collectAsState()
                val isInsideGenerateStoryScreen = (currentTab == ScreenTab.DICTIONARY && isGenerateStoryActive)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentTab != ScreenTab.READER) {
                            Surface(
                                color = Color(0xFF090A0D),
                                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bottom_nav")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ScreenTab.values().forEach { tab ->
                                        val isSelected = currentTab == tab
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { currentTab = tab }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                .testTag("nav_item_${tab.route}")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (isSelected) Color(0x263B82F6) else Color.Transparent
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) tab.icon else tab.outlinedIcon,
                                                    contentDescription = tab.title,
                                                    tint = if (isSelected) Color(0xFF3B82F6) else Color(0xFF64748B),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Text(
                                                text = tab.title,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF64748B),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Apply scaffold content padding to individual screen containers to support standard notch & gesture areas
                        val screenModifier = Modifier.padding(innerPadding)
                        when (currentTab) {
                            ScreenTab.LIBRARY -> LibraryScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                modifier = screenModifier
                            )
                            ScreenTab.READER -> ReaderScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                modifier = screenModifier
                            )
                            ScreenTab.DICTIONARY -> DictionaryScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                modifier = screenModifier
                            )
                            ScreenTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                modifier = screenModifier
                            )
                        }

                        // Floating Background Story Generation Indicator / Notification
                        AnimatedVisibility(
                            visible = !isInsideGenerateStoryScreen && (storyGenState.isGenerating || (storyGenState.showBanner && storyGenState.savedBookId != null)),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = if (currentTab == ScreenTab.READER) 24.dp else innerPadding.calculateBottomPadding() + 12.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                        ) {
                            if (storyGenState.isGenerating) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shadowElevation = 8.dp,
                                    tonalElevation = 6.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.setGenerateStoryActive(true)
                                            currentTab = ScreenTab.DICTIONARY
                                        }
                                        .testTag("story_generation_progress_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Генерация текста: ${storyGenState.progressStage.ifBlank { "В процессе..." }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else if (storyGenState.savedBookId != null && storyGenState.showBanner) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 10.dp,
                                    tonalElevation = 6.dp,
                                    border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("story_generation_completed_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Рассказ готов!",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF059669)
                                            )
                                            Text(
                                                text = storyGenState.storyTitle ?: "Сгенерированный рассказ",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val bookId = storyGenState.savedBookId
                                                viewModel.dismissStoryGenerationBanner()
                                                if (bookId != null) {
                                                    viewModel.selectBook(bookId)
                                                    currentTab = ScreenTab.READER
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF059669)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Читать", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(
                                            onClick = { viewModel.dismissStoryGenerationBanner() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Закрыть",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data
        if ((android.content.Intent.ACTION_VIEW == action || android.content.Intent.ACTION_EDIT == action) && uri != null) {
            viewModel.importMultipleBooks(applicationContext, listOf(uri)) { success, failed ->
                val msg = if (success > 0) "Импортирован файл из внешнего источника" else "Не удалось открыть файл"
                android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
