package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `word_activity_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `word` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `language` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_activity_events_timestamp` ON `word_activity_events` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_activity_events_language_timestamp` ON `word_activity_events` (`language`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_activity_events_word_language` ON `word_activity_events` (`word`, `language`)")
    }
}

@Database(
    entities = [KnownWord::class, BookEntity::class, ChapterEntity::class, StudyWord::class, WordActivityEvent::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knownWordDao(): KnownWordDao
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun studyWordDao(): StudyWordDao
    abstract fun wordActivityDao(): WordActivityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lesewort_database"
                )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
