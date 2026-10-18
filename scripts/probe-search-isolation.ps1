param(
    [string]$Serial,
    [string]$Python = 'python',
    [ValidateSet('guards', 'search')][string]$Suite = 'guards',
    [ValidateSet('text-1000.pdf', 'images-430.pdf', 'images-1000.pdf', 'dense-100.pdf')][string]$Fixture = 'text-1000.pdf',
    [ValidateSet('single-keep', 'single-reopen', 'dual-keep', 'dual-reopen')][string]$Mode = 'single-keep'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'android-env.ps1')
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$devices = @((& $adb devices) | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($LASTEXITCODE -ne 0) { throw 'Device enumeration failed.' }
if ($Serial) {
    if ($Serial -notin $devices) { throw 'Selected device not authorized.' }
} elseif ($devices.Count -eq 1) {
    $Serial = $devices[0]
} else {
    throw 'Connect one authorized device; identifiers are not printed.'
}
Push-Location $projectRoot
try {
    $wrapperProperties = Get-Content gradle/wrapper/gradle-wrapper.properties -Raw
    if ($wrapperProperties -notmatch 'distributionUrl=https\\://services\.gradle\.org/distributions/gradle-8\.9-bin\.zip' -or
        $wrapperProperties -notmatch 'distributionSha256Sum=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab') {
        throw 'Wrapper differs from the approved 8.9 contract. Preserve the change and resolve it before using this script.'
    }
    & $Python scripts/generate-search-probe-fixtures.py
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic generation failed.' }
    & ./gradlew.bat :searchprobe:assembleDebug :searchprobe:lintDebug --dependency-verification strict --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Diagnostic APK checks failed.' }
    & $Python scripts/run-search-probe.py --adb $adb --serial $Serial --install-apk --suite $Suite --fixture $Fixture --mode $Mode
    if ($LASTEXITCODE -ne 0) { throw 'Diagnostic guards/probe failed; reader APK was not changed.' }
} finally {
    # Only our separate diagnostic package; never force-stop or clear the reader.
    & $Python (Join-Path $PSScriptRoot 'run-search-probe.py') --adb $adb --serial $Serial --cleanup-only
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Diagnostic cleanup failed; reconnect to stop the probe package.' }
    Pop-Location
}
