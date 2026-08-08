import re

with open('app/src/main/java/com/aman/gigi/db/ScreensaverDatabase.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add model and dao imports
if "com.aman.gigi.model.BreakCardSessionMirror" not in content:
    content = content.replace(
        "import com.aman.gigi.model.ChatMessage",
        "import com.aman.gigi.model.ChatMessage\nimport com.aman.gigi.model.BreakCardSessionMirror\nimport com.aman.gigi.data.dao.BreakCardDao"
    )

# 2. Add entity class
content = content.replace(
    "com.aman.gigi.model.ChatMessage::class\n    ],",
    "com.aman.gigi.model.ChatMessage::class,\n        BreakCardSessionMirror::class\n    ],"
)

# 3. Update version
content = content.replace("version = 24,", "version = 25,")

# 4. Add DAO abstract fun
content = content.replace(
    "abstract fun chatDao(): ChatDao",
    "abstract fun chatDao(): ChatDao\n    abstract fun breakCardDao(): BreakCardDao"
)

# 5. Add migration 24->25
migration_code = """        val MIGRATION_24_25: Migration = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `break_card_sessions` (`sessionId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `cardName` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `senderDeviceId` TEXT NOT NULL, `senderName` TEXT NOT NULL, `animatedSvgUrl` TEXT, `responsesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_break_card_sessions_connectionId_updatedAt` ON `break_card_sessions` (`connectionId`, `updatedAt`)")
            }
        }

        val MIGRATION_23_24"""

content = content.replace("val MIGRATION_23_24", migration_code)

with open('app/src/main/java/com/aman/gigi/db/ScreensaverDatabase.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated ScreensaverDatabase.kt")
