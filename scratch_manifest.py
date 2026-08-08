import re

with open('app/src/main/AndroidManifest.xml', 'r', encoding='utf-8') as f:
    content = f.read()

receivers_xml = """        <receiver
            android:name=".widget.SharedCountdownWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/shared_countdown_widget_info" />
        </receiver>

        <receiver
            android:name=".widget.LatestNoteWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/latest_note_widget_info" />
        </receiver>
"""

# Insert before </application>
content = content.replace("</application>", receivers_xml + "\n    </application>")

with open('app/src/main/AndroidManifest.xml', 'w', encoding='utf-8') as f:
    f.write(content)
