param([string]$Serial, [switch]$SkipTests, [switch]$Probe, [switch]$Profile, [ValidateRange(10,600)][int]$Seconds = 30, [ValidateRange(1,3)][int]$Runs = 3)

$ErrorActionPreference = 'Stop'
if ($Probe -and $SkipTests) { throw '-Probe cannot be combined with -SkipTests.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'android-env.ps1')
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$lines = & $adb devices
if ($LASTEXITCODE -ne 0) { throw 'adb could not enumerate devices.' }
$devices = @($lines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) {
    if ($Serial -notin $devices) { throw 'The selected device is not connected and authorized.' }
} elseif ($devices.Count -eq 1) {
    $Serial = $devices[0]
} elseif ($devices.Count -eq 0) {
    throw 'No authorized Android device. Enable USB debugging, connect a data cable, unlock the phone, and accept the PC prompt.'
} else {
    throw 'Multiple devices found. Select one with -Serial (do not publish its identifier).'
}

function Invoke-DeviceAdb([string[]]$DeviceArguments) {
    $output = & $adb -s $Serial @DeviceArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb command failed: $($DeviceArguments[0])" }
    return $output
}

$api = Invoke-DeviceAdb @('shell', 'getprop', 'ro.build.version.sdk')
if ([int]($api | Select-Object -Last 1) -lt 23) { throw 'Android API 23 or newer is required.' }
Write-Host "Authorized device: Android API $api (identifier omitted)"

Push-Location $projectRoot
try {
    $variant = if ($Profile) { 'profile' } else { 'debug' }
    $taskVariant = if ($Profile) { 'Profile' } else { 'Debug' }
    $tasks = @(":app:assemble$taskVariant", "-Pp0TestBuildType=$variant")
    if (-not $SkipTests) { $tasks += ":app:assemble${taskVariant}AndroidTest" }
    & ./gradlew.bat @tasks --dependency-verification strict --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    Invoke-DeviceAdb @('install', '-r', "app/build/outputs/apk/$variant/app-$variant.apk") | Write-Host
    if (-not $SkipTests) {
        Invoke-DeviceAdb @('install', '-r', "app/build/outputs/apk/androidTest/$variant/app-$variant-androidTest.apk") | Write-Host
        $arguments = @('shell', 'am', 'instrument', '-w')
        if ($Probe) { $arguments += @('-e', 'probe', 'true', '-e', 'seconds', [string]$Seconds, '-e', 'runs', [string]$Runs) }
        $arguments += 'dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation'
        $result = Invoke-DeviceAdb $arguments
        $result | Write-Host
        $expectedTests = if ($Probe) { 1 } else { 41 }
        if (($result -join "`n") -notmatch "P0 tests=$expectedTests, failures=0") {
            throw 'Device tests did not report the expected successes.'
        }
        if ($Probe) {
            $reportLine = $result | Where-Object { $_ -like 'INSTRUMENTATION_STATUS: p0Report=*' } | Select-Object -Last 1
            if (-not $reportLine) { throw 'Probe did not return a report.' }
            $report = $reportLine.Substring('INSTRUMENTATION_STATUS: p0Report='.Length) | ConvertFrom-Json
            if ($report.runs.Count -ne $Runs) { throw 'Incomplete probe run count.' }
            $report | ConvertTo-Json -Depth 10 | Set-Content "local-artifacts/p0-$variant-probe.json" -Encoding utf8
            Get-FileHash "app/build/outputs/apk/$variant/app-$variant.apk" -Algorithm SHA256 |
                Select-Object Algorithm,Hash | ConvertTo-Json | Set-Content "local-artifacts/p0-$variant-probe-apk.json"
            Write-Host "Saved local-artifacts/p0-$variant-probe.json (instrumentation summary)."
        }
    }
    $launch = Invoke-DeviceAdb @('shell', 'am', 'start', '-W', '-n', 'dev.example.ebookviewer.p0/dev.example.ebookviewer.MainActivity')
    if (($launch -join "`n") -notmatch 'Status: ok') { throw 'Activity launch was not confirmed.' }
    Write-Host 'Activity launch confirmed. Manually check PDF reading, center tap, settings orientation, pinch, fling, and document switching.'
} finally {
    Pop-Location
}
