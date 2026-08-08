import re

with open('app/src/main/java/com/aman/gigi/hilt/DatabaseModule.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add import
if "BreakCardDao" not in content:
    content = content.replace(
        "import com.aman.gigi.data.dao.ConnectionDao",
        "import com.aman.gigi.data.dao.ConnectionDao\nimport com.aman.gigi.data.dao.BreakCardDao"
    )

    # Add provides function
    provides_code = """    @Provides
    fun provideBreakCardDao(database: ScreensaverDatabase): BreakCardDao {
        return database.breakCardDao()
    }
}"""

    content = content.replace("}", provides_code)
    # The above replace will replace ALL '}' so we need to be more precise
    content = re.sub(r'}\s*$', provides_code, content)

with open('app/src/main/java/com/aman/gigi/hilt/DatabaseModule.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated DatabaseModule.kt")
