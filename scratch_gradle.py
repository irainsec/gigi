import re

with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    content = f.read()

glance_dep = """    // Glance Widgets
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    testImplementation"""

content = content.replace("    testImplementation", glance_dep)

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(content)
