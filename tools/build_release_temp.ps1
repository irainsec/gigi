$ErrorActionPreference = 'Stop'

$src = 'C:\Users\ATPL-ADMIN\Downloads\gigi'
$tmp = 'C:\Users\ATPL-ADMIN\Downloads\gigi_release_build'

if (Test-Path $tmp) {
    Remove-Item -Recurse -Force $tmp
}

New-Item -ItemType Directory -Path $tmp | Out-Null
robocopy $src $tmp /MIR /XD .git .gradle build app\build gigi-server\node_modules gigi-server\captures /XF *.log | Out-Null

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $tmp
try {
    .\gradlew.bat :app:assembleRelease
    $builtApk = Join-Path $tmp 'app\build\outputs\apk\release\app-release.apk'
    $destDir = 'C:\Users\ATPL-ADMIN\Downloads\gigi\app\build\outputs\apk\release'
    if (!(Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
    Copy-Item $builtApk (Join-Path $destDir 'app-release.apk') -Force
    Write-Output $builtApk
}
finally {
    Pop-Location
}
