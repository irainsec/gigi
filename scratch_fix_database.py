import re

# Fix ReminderDatabase.kt
with open('app/src/main/java/com/aman/gigi/db/ReminderDatabase.kt', 'r', encoding='utf-8') as f:
    db_content = f.read()

db_content = db_content.replace('version = 9', 'version = 10')

mig9_10 = """
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_items ADD COLUMN emoji TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE shared_alarm_mirrors ADD COLUMN emoji TEXT DEFAULT NULL")
            }
        }
    }
}
"""

db_content = db_content.replace('    }\n}', mig9_10)

with open('app/src/main/java/com/aman/gigi/db/ReminderDatabase.kt', 'w', encoding='utf-8') as f:
    f.write(db_content)

# Fix AppModule.kt
with open('app/src/main/java/com/aman/gigi/hilt/AppModule.kt', 'r', encoding='utf-8') as f:
    app_mod = f.read()

app_mod = app_mod.replace('ReminderDatabase.MIGRATION_8_9', 'ReminderDatabase.MIGRATION_8_9,\n                ReminderDatabase.MIGRATION_9_10')

with open('app/src/main/java/com/aman/gigi/hilt/AppModule.kt', 'w', encoding='utf-8') as f:
    f.write(app_mod)
