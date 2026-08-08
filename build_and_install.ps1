
Write-Host "Checking for Android SDK..."

$adbPath = "C:\Users\ATPL-ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$javaPath = "C:\Program Files\Android\Android Studio1\jbr\bin\java.exe"

if (-not (Test-Path $adbPath)) {
    Write-Error "ADB not found at $adbPath"
    exit 1
}

if (-not (Test-Path $javaPath)) {
    Write-Error "Java not found at $javaPath"
    exit 1
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$env:ANDROID_HOME = "C:\Users\ATPL-ADMIN\AppData\Local\Android\Sdk"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\tools;$env:JAVA_HOME\bin;$env:Path"

Write-Host "Building APK..."
# We use cmd /c to run gradlew.bat because invoking .bat directly in PS sometimes has issues with env vars
cmd /c "set ""JAVA_HOME=$($env:JAVA_HOME)"" && set ""ANDROID_HOME=$($env:ANDROID_HOME)"" && .\gradlew.bat :app:assembleDebug"

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Successful. Installing..."
    # List devices
    & $adbPath devices
    
    # Parse and install
    $devices = & $adbPath devices | Select-String -Pattern "\tdevice$"
    if ($devices) {
        foreach ($dev in $devices) {
            $devId = $dev.ToString().Split("`t")[0]
            Write-Host "Installing on $devId..."
            & $adbPath -s $devId install -r app\build\outputs\apk\debug\app-debug.apk
            
            # Launch the app
            & $adbPath -s $devId shell monkey -p com.aman.gigi -c android.intent.category.LAUNCHER 1
        }
    } else {
        Write-Warning "No devices connected."
    }
} else {
    Write-Error "Build Failed with exit code $LASTEXITCODE"
}
