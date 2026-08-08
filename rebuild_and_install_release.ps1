
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$env:ANDROID_HOME = "C:\Users\ATPL-ADMIN\AppData\Local\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

Write-Host "Building Release APK..."
./gradlew.bat assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Successful. Searching for APK..."
    $apk = Get-ChildItem -Path "app\build\outputs\apk\release" -Filter "*.apk" -Recurse | Select-Object -First 1
    if ($apk) {
        Write-Host "Installing $($apk.FullName)..."
        adb install -r $apk.FullName
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Installation Successful. Launching app..."
            adb shell monkey -p com.aman.gigi -c android.intent.category.LAUNCHER 1
        } else {
            Write-Error "Installation Failed."
        }
    } else {
        Write-Error "APK not found in app\build\outputs\apk\release"
    }
} else {
    Write-Error "Build Failed."
}
