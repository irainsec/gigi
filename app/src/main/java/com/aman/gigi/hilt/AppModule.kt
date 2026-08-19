package com.aman.gigi.hilt

import android.content.Context
import androidx.room.Room
import com.aman.gigi.db.ReminderDAO
import com.aman.gigi.db.ReminderDatabase
import com.aman.gigi.db.SharedAlarmDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideDatabase(context: Context): ReminderDatabase {
        return Room.databaseBuilder(
            context,
            ReminderDatabase::class.java,
            "reminder_items"
        )
            .addMigrations(
                ReminderDatabase.MIGRATION_1_2,
                ReminderDatabase.MIGRATION_2_3,
                ReminderDatabase.MIGRATION_3_4,
                ReminderDatabase.MIGRATION_4_5,
                ReminderDatabase.MIGRATION_5_6,
                ReminderDatabase.MIGRATION_6_7,
                ReminderDatabase.MIGRATION_7_8,
                ReminderDatabase.MIGRATION_8_9,
                ReminderDatabase.MIGRATION_9_10,
                ReminderDatabase.MIGRATION_10_11
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRecentPlayDao(database: ReminderDatabase): com.aman.gigi.db.RecentPlayDao {
        return database.recentPlayDao()
    }

    @Provides
    fun provideNoteDao(database: ReminderDatabase): ReminderDAO {
        return database.reminderDAO()
    }

    @Provides
    fun provideRecentlySentGifDao(database: ReminderDatabase): com.aman.gigi.db.RecentlySentGifDao {
        return database.recentlySentGifDao()
    }

    @Provides
    fun provideSharedAlarmDao(database: ReminderDatabase): SharedAlarmDao {
        return database.sharedAlarmDao()
    }

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): com.aman.gigi.utils.NetworkMonitor {
        return com.aman.gigi.utils.NetworkMonitor(context)
    }
}
