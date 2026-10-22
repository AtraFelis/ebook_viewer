$ErrorActionPreference = 'Stop'
$identityRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'android-env.ps1')
$aapt = Join-Path $env:ANDROID_HOME 'build-tools/34.0.0/aapt2.exe'
$versions = Get-Content (Join-Path $identityRoot 'gradle.properties') -Raw | ConvertFrom-StringData
$code = $versions['ebook.versionCode']
$name = $versions['ebook.versionName']
$variants = @(
    @{ file='app/build/outputs/apk/debug/app-debug.apk'; package='dev.example.ebookviewer.p0'; version="$name-dev"; debug=$true },
    @{ file='app/build/outputs/apk/release/app-release-unsigned.apk'; package='dev.example.ebookviewer'; version=$name; debug=$false }
)
foreach ($variant in $variants) {
    $metadata = (& $aapt dump badging (Join-Path $identityRoot $variant.file) | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata inspection failed.' }
    $expected = "package: name='$($variant.package)' versionCode='$code' versionName='$($variant.version)'"
    if (-not $metadata.Contains($expected)) { throw 'Unexpected APK identity/version.' }
    if (($metadata -match 'application-debuggable') -ne $variant.debug) { throw 'Unexpected debuggable flag.' }
    Write-Host "Verified $($variant.package) $($variant.version) / versionCode$code / debuggable=$($variant.debug)"
}
