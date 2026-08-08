package com.aman.gigi.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aman.gigi.model.RecurrencePatternConverter
import com.aman.gigi.model.Reminder
import com.aman.gigi.model.SharedAlarmMirror

@Database(entities = [Reminder::class, RecentlySentGif::class, SharedAlarmMirror::class], version = 10, exportSchema = false)
@TypeConverters(RecurrencePatternConverter::class)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDAO(): ReminderDAO
    abstract fun recentlySentGifDao(): RecentlySentGifDao
    abstract fun sharedAlarmDao(): SharedAlarmDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN recurrencePattern TEXT DEFAULT NULL")
            }
        }
        
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS caller_themes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        contactId TEXT NOT NULL,
                        themeType TEXT NOT NULL,
                        wallpaperPath TEXT,
                        videoUri TEXT,
                        lottieAssetPath TEXT,
                        blurIntensity REAL NOT NULL,
                        animationSpeed REAL NOT NULL,
                        isDepthEnabled INTEGER NOT NULL,
                        overlayEffect TEXT,
                        dynamicConfig TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE caller_themes ADD COLUMN dynamicConfig TEXT")
                } catch (e: Exception) {
                }
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN customIntervalMillis INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN repeatStartHour INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN repeatStartMinute INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN repeatEndHour INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN repeatEndMinute INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS caller_themes")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recently_sent_gifs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `url` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recently_sent_gifs_url` ON `recently_sent_gifs` (`url`)")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shared_alarm_mirrors` (
                        `alarmId` TEXT NOT NULL,
                        `connectionId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT,
                        `dueAt` INTEGER NOT NULL,
                        `recurrencePattern` TEXT,
                        `customIntervalMillis` INTEGER,
                        `repeatStartHour` INTEGER,
                        `repeatStartMinute` INTEGER,
                        `repeatEndHour` INTEGER,
                        `repeatEndMinute` INTEGER,
                        `ownerMemberId` TEXT,
                        `ownerDisplayName` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`alarmId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN emoji TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE shared_alarm_mirrors ADD COLUMN emoji TEXT DEFAULT NULL")
            }
        }
    }
}

