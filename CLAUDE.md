# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 📱 Project Overview

**Lesewort** is an Android application for intelligent reading of literature and subtitles in foreign languages (German, English, French) with deep linguistic integration. The app uses Jetpack Compose for UI and follows a clean architecture pattern with MVVM.

## 🔧 Development Commands

### Building the Application
```bash
# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease

# Install debug APK on connected device/emulator
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run all instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run a specific test class
./gradlew test --tests com.example.BookParserChunkingTest

# Run tests with coverage
./gradlew jacocoTestReport
```

### Code Quality & Analysis
```bash
# Check for lint issues
./gradlew lint

# Format code (if formatter is configured)
./gradlew spotlessApply
```

## 🏗️ Code Architecture

### High-Level Structure
The app follows a clean architecture with three main layers:

1. **UI Layer** (`ui/`): Jetpack Compose screens and components
2. **ViewModel Layer** (`MainViewModel.kt`): Central state management and business logic
3. **Data Layer** (`data/`): Repository, database, API clients, and engines

### Key Components

#### Entry Point
- `MainActivity.kt`: Initializes app, sets up bottom navigation, handles file intents

#### Navigation
- `Navigation.kt`: Defines screen tabs (LIBRARY, READER, DICTIONARY, SETTINGS)
- Bottom navigation enables switching between core app sections

#### Data Layer
- **Room Database** (`data/db/`): 
  - `AppDatabase.kt`: Database singleton with migrations
  - `Entities.kt`: Data models (KnownWord, BookEntity, ChapterEntity, StudyWord, WordActivityEvent)
  - `Dao.kt`: Data access objects with Flow and suspend functions
  - `BookRepository.kt`: Single source of truth for all data operations

#### APIs & Services
- **GeminiStoryService.kt**: Google Gemini API for story generation
- **GroqAPI.kt**: Groq API for contextual translation and word analysis
- **LinguisticEngine.kt**: Word tokenization and complexity calculation
- **BookParser.kt**: EPUB, SRT, VTT, TXT file parsing

#### UI Components
- **Screens** (`ui/screens/`): LibraryScreen, ReaderScreen, DictionaryScreen, GenerateStoryScreen, SettingsScreen, WordStatsView
- **Components** (`ui/components/`): BookCoverView, FlagIcons
- **Theme** (`ui/theme/`): Material 3 theming with custom word highlighting logic

### Data Flow
UI Events → ViewModel (StateFlow) → Repository → Room Database/Remote APIs → ViewModel → UI (recomposition)

### Word Status System
- **Known Words** (`known_words` table):
  - Status 0: New (default text color)
  - Status 1: Learning (orange/user-configurable)
  - Status 2: Learned (green)
- **Study Words** (`study_words` table): Violet-highlighted words for Match Game

## 📱 Key Features Implementation

### Reading Experience
- Text tokenization via `LinguisticEngine.tokenizeWord()`
- Dynamic word highlighting based on status colors
- Tap-to-translate via Groq API with TTS pronunciation
- Status cycling: New → Learning → Learned

### Story Generation
- CEFR level-based book generation via Gemini API
- Custom vocabulary integration from user's known words
- Progress overlay visible across all screens

### Dictionary & Training
- Search/filter across all words
- Match Game training using `study_words`
- Activity statistics calendar view

## 🧠 Important Conventions

### Color Coding (Strictly Enforced)
- **Learning Status**: Orange (user-configurable in Settings)
- **Learned Status**: Green (fixed)
- **Study Words ("Desired")**: Violet (fixed, used only in Match Game)
- These color systems are completely isolated and never overlap

### Testing Requirements
- All clickable elements must have `Modifier.testTag("snake_case_id")`
- UI tests validate both functionality and visual presentation
- Unit tests cover business logic and data transformations

### File Handling
- Supports EPUB (chapter extraction), SRT, VTT, TXT formats
- Processes incoming intents from other apps for file opening
- Automatic cover image extraction from EPUB files

## ⚠️ Critical Constraints

1. **Never modify**:
   - `metadata.json` without updating `strings.xml` correspondingly
   - `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` in `metadata.json`
   - Debug keystore or signing configurations
   - `local.properties` file creation

2. **Never use**:
   - Mock/fake data for features requiring real books or translations
   - Blocking UI operations (always use Dispatchers.IO or Coroutines)

3. **Always**:
   - Use Kotlin Coroutines Flow for asynchronous operations
   - Store Gemini API keys via BuildConfig (Secrets Gradle Plugin)
   - Store Groq API keys in SharedPreferences

## 🔍 Debugging & Investigation

### Useful Logcat Tags
- `Lesewort`: General application logging
- `BookParser`: File parsing operations
- `GeminiService`: Story generation API calls
- `GroqService`: Translation and analysis API calls
- `WordHighlight`: Text coloring operations

### Common Investigation Points
1. **Word highlighting issues**: Check `ReaderScreen.kt` and `ThemeSettings.kt`
2. **Database problems**: Inspect `BookRepository.kt` and DAO implementations
3. **API failures**: Review `GroqAPI.kt` and `GeminiStoryService.kt` error handling
4. **Navigation issues**: Verify `Navigation.kt` and `MainActivity.kt` setup
5. **Story generation**: Check `GenerateStoryScreen.kt` and background service logic

This guide should help you quickly understand and contribute to the Lesewort codebase. When in doubt, refer to the PROJECT_AUDIT.md file for detailed architectural decisions and conventions.