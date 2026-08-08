# Gigi App Signing Setup

This document outlines the signing configuration for the production (release) variant of the Gigi application.

## 🔐 Keystore Information
- **File**: `my-release-key.jks` (Project Root)
- **Alias**: `my-key-alias`
- **Store Password**: `123456`
- **Key Password**: `123456`

## 🛠️ Build Process
The application is configured to automatically sign the release APK using the keystore above.

### Generating a Signed APK
To build the signed production APK, run:
```powershell
./gradlew.bat :app:assembleRelease
```

The output will be located at:
`app/build/outputs/apk/release/app-release.apk`

## 🛡️ ProGuard / R8 Protection
The project includes a `proguard-rules.pro` file configured to protect GSON, Hilt, Room, and your data models. **Do not remove these rules**, as they are essential for preventing crashes caused by code shrinking.

---

> [!CAUTION]
> **DO NOT LOSE THE KEYSTORE FILE.**
> If you lose `my-release-key.jks` or forget the passwords, you will be unable to update the application on the Play Store or for existing users. Keep a secure backup of this file.

> [!IMPORTANT]
> For security, it is recommended to eventually move these passwords to a `local.properties` file or environment variables instead of hardcoding them in `build.gradle.kts`.
