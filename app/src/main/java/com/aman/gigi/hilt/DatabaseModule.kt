package com.aman.gigi.hilt

import android.content.Context
import androidx.room.Room
import com.aman.gigi.db.ConnectionDao
import com.aman.gigi.db.LoveCardDao
import com.aman.gigi.db.OutboundActionDao
import com.aman.gigi.db.ScribbleDao
import com.aman.gigi.db.ScreensaverDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database and DAO instances
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideScreensaverDatabase(
        @ApplicationContext context: Context
    ): ScreensaverDatabase {
        return Room.databaseBuilder(
            context,
            ScreensaverDatabase::class.java,
            "screensaver_database"
        )
            .addMigrations(ScreensaverDatabase.MIGRATION_3_4)
            .addMigrations(ScreensaverDatabase.MIGRATION_4_5)
            .addMigrations(ScreensaverDatabase.MIGRATION_7_8)
            .addMigrations(ScreensaverDatabase.MIGRATION_8_9)
            .addMigrations(ScreensaverDatabase.MIGRATION_9_10)
            .addMigrations(ScreensaverDatabase.MIGRATION_10_11)
            .addMigrations(ScreensaverDatabase.MIGRATION_11_12)
            .addMigrations(ScreensaverDatabase.MIGRATION_12_13)
            .addMigrations(ScreensaverDatabase.MIGRATION_13_14)
            .addMigrations(ScreensaverDatabase.MIGRATION_14_15)
            .addMigrations(ScreensaverDatabase.MIGRATION_15_16)
            .addMigrations(ScreensaverDatabase.MIGRATION_17_18)
            .addMigrations(ScreensaverDatabase.MIGRATION_18_19)
            .addMigrations(ScreensaverDatabase.MIGRATION_19_20)
            .addMigrations(ScreensaverDatabase.MIGRATION_24_25)
            .addMigrations(ScreensaverDatabase.MIGRATION_25_26)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideConnectionDao(database: ScreensaverDatabase): ConnectionDao {
        return database.connectionDao()
    }
    
    @Provides
    @Singleton
    fun provideScribbleDao(database: ScreensaverDatabase): ScribbleDao {
        return database.scribbleDao()
    }

    @Provides
    @Singleton
    fun provideOutboundActionDao(database: ScreensaverDatabase): OutboundActionDao {
        return database.outboundActionDao()
    }

    @Provides
    @Singleton
    fun provideLoveCardDao(database: ScreensaverDatabase): LoveCardDao {
        return database.loveCardDao()
    }

    @Provides
    @Singleton
    fun provideBreakCardDao(database: ScreensaverDatabase): com.aman.gigi.data.dao.BreakCardDao {
        return database.breakCardDao()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: ScreensaverDatabase): com.aman.gigi.db.ChatDao {
        return database.chatDao()
    }
}
