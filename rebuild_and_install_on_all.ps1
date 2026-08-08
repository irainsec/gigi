
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$env:ANDROID_HOME = "C:\Users\ATPL-ADMIN\AppData\Local\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

Write-Host "Cleaning Project..."
./gradlew.bat clean

Write-Host "Building Release APK..."
./gradlew.bat assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Successful. Searching for APK..."
    $apk = Get-ChildItem -Path "app\build\outputs\apk\release" -Filter "*.apk" -Recurse | Select-Object -First 1
    if ($apk) {
        Write-Host "Found APK: $($apk.FullName)"
        
        $devices = adb devices | Select-String -Pattern "\tdevice$"
        if ($devices) {
            foreach ($deviceLine in $devices) {
                $deviceId = $deviceLine.ToString().Split("`t")[0].Trim()
                Write-Host "Installing on device: $deviceId..."
                adb -s $deviceId install -r $apk.FullName
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "Installation Successful on $deviceId. Launching app..."
                    adb -s $deviceId shell monkey -p com.aman.gigi -c android.intent.category.LAUNCHER 1
                } else {
                    Write-Error "Installation Failed on $deviceId."
                }
            }
        } else {
            Write-Error "No devices connected."
        }
    } else {
        Write-Error "APK not found in app\build\outputs\apk\release"
    }
} else {
    Write-Error "Build Failed."
}
