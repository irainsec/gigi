# Publishes the current release APKs to the website so users can download/update
# straight from https://gigi.iamanraj.com — no Play Store, no server rebuild
# (downloads/ is bind-mounted into the container).
#
#   .\tools\publish_apk.ps1              # publish the existing release APKs
#   .\tools\publish_apk.ps1 -Build       # build them first, then publish
#
# The build produces one APK per ABI plus a universal one. Phones are pointed at the
# APK matching their own architecture (~44 MB smaller than universal); the universal
# build stays as the website download and the fallback for anything unrecognised.

param([switch]$Build)

$ErrorActionPreference = "Stop"
$root    = Split-Path -Parent $PSScriptRoot
$apkDir  = Join-Path $root "app\build\outputs\apk\release"
$dest    = Join-Path $root "gigi-server\downloads"
$abis    = @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

if ($Build) {
    Write-Host "Building release APKs..." -ForegroundColor Cyan
    $env:TMP = "C:\T"; $env:TEMP = "C:\T"
    & (Join-Path $root "gradlew.bat") -p $root :app:assembleRelease | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
}

# The universal APK is required; per-ABI ones are published when present.
$universal = Join-Path $apkDir "app-universal-release.apk"
if (-not (Test-Path $universal)) {
    # Pre-split builds emitted a single app-release.apk
    $legacy = Join-Path $apkDir "app-release.apk"
    if (Test-Path $legacy) { $universal = $legacy }
    else { throw "No universal APK at $universal — run with -Build first." }
}
if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }

# versionName / versionCode straight from the built APK
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$aapt = Get-ChildItem "$sdk\build-tools\*\aapt2.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
$version = "unknown"; $code = 0
if ($aapt) {
    $dump = & $aapt dump badging $universal 2>$null | Out-String
    if ($dump -match "versionName='([^']+)'") { $version = $matches[1] }
    if ($dump -match "versionCode='([^']+)'") { $code = [int]$matches[1] }
}
if ($code -eq 0) { throw "Could not read versionCode from the APK — is aapt2 available?" }

# Every build is published twice: under a stable alias (for the website, and as a
# fallback) and under an immutable versioned name. Only the versioned name can be
# cached hard at the edge, which is what stops every phone pulling 25 MB from this
# machine over a home uplink.
function Publish-Apk($src, $stableName, $versionedName) {
    Copy-Item $src (Join-Path $dest $stableName) -Force
    if ($versionedName) { Copy-Item $src (Join-Path $dest $versionedName) -Force }
    $hash = (Get-FileHash $src -Algorithm SHA256).Hash.ToLower()
    $mb   = [Math]::Round((Get-Item $src).Length / 1MB, 1)
    return @{ name = $stableName; versioned = $versionedName; sha256 = $hash; sizeMb = $mb }
}

# Filename-safe version tag, e.g. v1.9.0 -> 1.9.0
$verTag = ($version -replace '^v', '') -replace '[^0-9A-Za-z._-]', '-'

$base = "https://gigi.iamanraj.com/downloads"
$uni  = Publish-Apk $universal "gigi-latest.apk" "gigi-$verTag-universal.apk"
Write-Host ("  universal      {0,6} MB" -f $uni.sizeMb) -ForegroundColor DarkGray

$abiUrls = @{}; $abiSha = @{}; $abiSize = @{}
foreach ($abi in $abis) {
    $src = Join-Path $apkDir "app-$abi-release.apk"
    if (-not (Test-Path $src)) { continue }
    $info = Publish-Apk $src "gigi-$abi.apk" "gigi-$verTag-$abi.apk"
    # Point the updater at the immutable name so Cloudflare can serve it from a PoP.
    $abiUrls[$abi] = "$base/gigi-$verTag-$abi.apk"
    $abiSha[$abi]  = $info.sha256
    $abiSize[$abi] = $info.sizeMb
    Write-Host ("  {0,-14} {1,6} MB" -f $abi, $info.sizeMb) -ForegroundColor DarkGray
}

# Release notes are hand-written, so carry the existing ones (and the iOS manifest
# link) forward instead of clobbering them — an earlier version of this script wrote
# a latest.json with the wrong field names, which silently broke the in-app updater.
$metaPath = Join-Path $dest "latest.json"
$notes    = "Bug fixes and improvements."
$iosUrl   = "itms-services://?action=download-manifest&url=https://gigi.iamanraj.com/downloads/manifest.plist"
if (Test-Path $metaPath) {
    $existing = Get-Content $metaPath -Raw | ConvertFrom-Json
    if ($existing.releaseNotes)   { $notes  = $existing.releaseNotes }
    if ($existing.iosDownloadUrl) { $iosUrl = $existing.iosDownloadUrl }
}

[ordered]@{
    updatedAt       = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    versionCode     = $code
    versionName     = $version
    downloadUrl     = "$base/gigi-$verTag-universal.apk"
    websiteUrl      = "$base/gigi-latest.apk"
    sha256          = $uni.sha256
    sizeMb          = $uni.sizeMb
    abiUrls         = $abiUrls
    abiSha256       = $abiSha
    abiSizeMb       = $abiSize
    iosDownloadUrl  = $iosUrl
    releaseNotes    = $notes
} | ConvertTo-Json -Depth 5 | Set-Content $metaPath -Encoding UTF8

Write-Host "Published $version (code $code) -> $base/gigi-latest.apk" -ForegroundColor Green
