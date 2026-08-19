package com.aman.gigi.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aman.gigi.model.Connection
import com.aman.gigi.model.LoveCardItemMirror
import com.aman.gigi.model.LoveCardResponseMirror
import com.aman.gigi.model.LoveCardStackMirror
import com.aman.gigi.model.OutboundAction
import com.aman.gigi.model.Scribble


/**
 * Room database for Connected Screensaver feature
 */
@Database(
    entities = [
        Connection::class,
        Scribble::class,
        OutboundAction::class,
        LoveCardStackMirror::class,
        LoveCardItemMirror::class,
        LoveCardResponseMirror::class,
        com.aman.gigi.model.ConnectionMember::class,
        com.aman.gigi.model.ChatMessage::class,
        com.aman.gigi.model.BreakCardSessionMirror::class
    ],
    version = 26,
    exportSchema = false
)
@TypeConverters(StrokeListConverter::class)
abstract class ScreensaverDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun scribbleDao(): ScribbleDao
    abstract fun outboundActionDao(): OutboundActionDao
    abstract fun loveCardDao(): LoveCardDao
    abstract fun chatDao(): ChatDao
    abstract fun breakCardDao(): com.aman.gigi.data.dao.BreakCardDao

    companion object {
        val MIGRATION_25_26: Migration = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN origin TEXT NOT NULL DEFAULT 'INVITE'")
                db.execSQL("ALTER TABLE connections ADD COLUMN trustRing INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25: Migration = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `break_card_sessions` (`sessionId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `cardName` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `senderDeviceId` TEXT NOT NULL, `senderName` TEXT NOT NULL, `animatedSvgUrl` TEXT, `responsesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_break_card_sessions_connectionId_updatedAt` ON `break_card_sessions` (`connectionId`, `updatedAt`)")
            }
        }

        val MIGRATION_23_24: Migration = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN partnerAvatarMode TEXT NOT NULL DEFAULT 'EMOJI'")
                db.execSQL("ALTER TABLE connections ADD COLUMN partnerTwigiUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connection_members ADD COLUMN emojiUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN partnerEmojiUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_messages (" +
                    "id TEXT NOT NULL, " +
                    "connectionId TEXT NOT NULL, " +
                    "senderDeviceId TEXT NOT NULL, " +
                    "senderName TEXT NOT NULL, " +
                    "isMine INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "text TEXT NOT NULL, " +
                    "gifUrl TEXT NOT NULL, " +
                    "sentAt INTEGER NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "PRIMARY KEY(id))"
                )
            }
        }

        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add isGroup to connections
                db.execSQL("ALTER TABLE connections ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
                
                // Add senderDeviceId to scribbles
                db.execSQL("ALTER TABLE scribbles ADD COLUMN senderDeviceId TEXT DEFAULT NULL")
                
                // Create connection_members table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS connection_members (" +
                    "connectionId TEXT NOT NULL, " +
                    "memberDeviceId TEXT NOT NULL, " +
                    "memberName TEXT NOT NULL, " +
                    "memberEmoji TEXT NOT NULL, " +
                    "memberAvatarUrl TEXT, " +
                    "role TEXT NOT NULL, " +
                    "joinedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(connectionId, memberDeviceId))"
                )
                
                // Migrate existing partnerDeviceId into connection_members
                db.execSQL(
                    "INSERT INTO connection_members (connectionId, memberDeviceId, memberName, memberEmoji, memberAvatarUrl, role, joinedAt) " +
                    "SELECT connectionId, partnerDeviceId, partnerName, partnerEmoji, partnerAvatarUrl, 'PARTNER', createdAt " +
                    "FROM connections WHERE partnerDeviceId IS NOT NULL AND partnerDeviceId != ''"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN connectionStatus TEXT NOT NULL DEFAULT 'CONNECTED'")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add fields from Phase 3 only
                db.execSQL("ALTER TABLE scribbles ADD COLUMN revealType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE scribbles ADD COLUMN secretMessage TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add meetingDate to connections
                db.execSQL("ALTER TABLE connections ADD COLUMN meetingDate INTEGER DEFAULT NULL")
                
                // Add meetingDate and anniversaryDate to scribbles
                db.execSQL("ALTER TABLE scribbles ADD COLUMN meetingDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE scribbles ADD COLUMN anniversaryDate INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN creatorDeviceId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN transportState TEXT NOT NULL DEFAULT 'CONNECTING'")
                db.execSQL("ALTER TABLE connections ADD COLUMN partnerPresence TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE connections ADD COLUMN lastSeenAt INTEGER DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outbound_actions (
                        id TEXT NOT NULL PRIMARY KEY,
                        connectionId TEXT NOT NULL,
                        actionType TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        localAssetPath TEXT,
                        remoteAssetUrl TEXT,
                        relatedScribbleId TEXT,
                        state TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        nextAttemptAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastError TEXT,
                        requiresDisplayReceipt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outbound_actions_connectionId_state_nextAttemptAt ON outbound_actions(connectionId, state, nextAttemptAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outbound_actions_relatedScribbleId ON outbound_actions(relatedScribbleId)")
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN role TEXT NOT NULL DEFAULT 'PARTNER'")
                db.execSQL("ALTER TABLE connections ADD COLUMN memberId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE connections ADD COLUMN serverArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE connections ADD COLUMN restoredAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN partnerAvatarUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS love_card_stacks (
                        stackId TEXT NOT NULL PRIMARY KEY,
                        connectionId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        senderMemberId TEXT,
                        senderDisplayName TEXT,
                        recipientMemberId TEXT,
                        status TEXT NOT NULL,
                        localState TEXT NOT NULL,
                        theme TEXT,
                        previewText TEXT,
                        isIncoming INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        openedAt INTEGER,
                        answeredAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS love_card_items (
                        cardId TEXT NOT NULL PRIMARY KEY,
                        stackId TEXT NOT NULL,
                        connectionId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        choicesJson TEXT,
                        theme TEXT,
                        animationStyle TEXT,
                        decorationsJson TEXT,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS love_card_responses (
                        responseId TEXT NOT NULL PRIMARY KEY,
                        stackId TEXT NOT NULL,
                        cardId TEXT NOT NULL,
                        answerText TEXT,
                        selectedChoice TEXT,
                        emojiReaction TEXT,
                        answeredAt INTEGER NOT NULL,
                        answeredByMemberId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_love_card_items_stackId_sortOrder ON love_card_items(stackId, sortOrder)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_love_card_stacks_connectionId_updatedAt ON love_card_stacks(connectionId, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_love_card_responses_stackId_cardId ON love_card_responses(stackId, cardId)")
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE love_card_items ADD COLUMN decorationsJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbound_actions ADD COLUMN targetDeviceId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN partnerEmoji TEXT NOT NULL DEFAULT '🌻'")
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN relationshipType TEXT NOT NULL DEFAULT 'ROMANTIC'")
            }
        }

        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE love_card_stacks ADD COLUMN unlockDate INTEGER DEFAULT NULL")
            }
        }
    }
}
