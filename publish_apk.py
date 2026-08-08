import os
import shutil
import json
import re
import datetime

staging_apk = "app/build/outputs/apk/staging/app-staging.apk"
release_apk = "app/build/outputs/apk/release/app-release.apk"
debug_apk = "app/build/outputs/apk/debug/app-debug.apk"
target_dir = "gigi-server/downloads"
target_apk = os.path.join(target_dir, "gigi-latest.apk")
target_meta = os.path.join(target_dir, "latest.json")

if os.path.exists(staging_apk):
    source_apk = staging_apk
elif os.path.exists(release_apk):
    source_apk = release_apk
else:
    source_apk = debug_apk

if not os.path.exists(source_apk):
    print(f"Error: Neither release APK ({release_apk}) nor debug APK ({debug_apk}) found!")
    exit(1)

os.makedirs(target_dir, exist_ok=True)

# Auto-read versionCode and versionName from build.gradle.kts
version_code = 11
version_name = "v1.5.1"
try:
    gradle_path = "app/build.gradle.kts"
    with open(gradle_path, "r", encoding="utf-8") as gf:
        content = gf.read()
        vc_match = re.search(r'versionCode\s*=\s*(\d+)', content)
        vn_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
        if vc_match:
            version_code = int(vc_match.group(1))
        if vn_match:
            version_name = vn_match.group(1)
    print(f"Detected version: {version_name} (code {version_code})")
except Exception as e:
    print(f"Warning: Could not read version from build.gradle.kts: {e}")

size_bytes = os.path.getsize(source_apk)
size_mb = round(size_bytes / (1024 * 1024), 1)
now_iso = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")

print(f"Publishing {source_apk} ({size_mb} MB) -> {target_apk}...")
shutil.copyfile(source_apk, target_apk)

target_plist = os.path.join(target_dir, "manifest.plist")

metadata = {
    "updatedAt": now_iso,
    "versionCode": version_code,
    "versionName": version_name,
    "downloadUrl": "https://gigi.iamanraj.com/downloads/gigi-latest.apk",
    "iosDownloadUrl": "itms-services://?action=download-manifest&url=https://gigi.iamanraj.com/downloads/manifest.plist",
    "releaseNotes": "Bug fixes, notification improvements, Bestie theme, storybook onboarding & stability updates.",
    "sizeMb": size_mb
}

with open(target_meta, "w", encoding="utf-8") as f:
    json.dump(metadata, f, indent=2)

# Generate iOS Over-The-Air (OTA) Manifest for iPhone Web Downloads
plist_content = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>items</key>
	<array>
		<dict>
			<key>assets</key>
			<array>
				<dict>
					<key>kind</key>
					<string>software-package</string>
					<key>url</key>
					<string>https://gigi.iamanraj.com/downloads/gigi-latest.ipa</string>
				</dict>
			</array>
			<key>metadata</key>
			<dict>
				<key>bundle-identifier</key>
				<string>com.aman.gigi</string>
				<key>bundle-version</key>
				<string>{version_name}</string>
				<key>kind</key>
				<string>software</string>
				<key>title</key>
				<string>Gigi</string>
			</dict>
		</dict>
	</array>
</dict>
</plist>
"""

with open(target_plist, "w", encoding="utf-8") as pf:
    pf.write(plist_content)

print(f"Successfully published {version_name} (code {version_code}) to server downloads (Android APK + iOS Manifest)!")
