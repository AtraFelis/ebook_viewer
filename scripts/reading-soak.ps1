param([ValidateSet('preflight', 'install-test', 'run', 'progress', 'collect', 'stop')][string]$Mode = 'preflight')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-env.ps1')
$soakRoot = Split-Path $PSScriptRoot -Parent
$soakArtifacts = Join-Path $soakRoot 'local-artifacts'
$soakAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$soakRunPath = Join-Path $soakArtifacts 'reading-soak-run.txt'
function Invoke-SoakAdb([string[]]$Arguments, [int]$Deadline = 45) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $soakAdb
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw 'ADB did not start' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($Deadline * 1000)) {
            $process.Kill()
            throw 'ADB deadline exceeded; not a pass'
        }
        $result = $stdout.GetAwaiter().GetResult()
        $errorText = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw 'ADB operation failed; device details withheld' }
        return $result
    } finally { $process.Dispose() }
}
if ($Mode -eq 'stop') {
    try {
        try { Invoke-SoakAdb @('shell', 'am', 'force-stop', 'dev.example.ebookviewer.p0.test') | Out-Null }
        finally { Invoke-SoakAdb @('shell', 'am', 'force-stop', 'dev.example.ebookviewer.p0') | Out-Null }
    } finally { Invoke-SoakAdb @('kill-server') | Out-Null }
    'App, instrumentation and ADB stopped'
    exit
}
$devices = @(((Invoke-SoakAdb @('devices')) -split "`n") | Where-Object { $_ -match '^\S+\s+device\s*$' })
if ($devices.Count -ne 1) { throw 'Exactly one authorized device required; no workload started' }
$soakPrefix = @('-s', ($devices[0] -split '\s+')[0])
$api = (Invoke-SoakAdb ($soakPrefix + @('shell', 'getprop', 'ro.build.version.sdk'))).Trim()
if ($api -ne '33') { throw 'This local soak plan is limited to the current API 33 device' }
if ($Mode -in @('progress', 'collect')) {
    $expectedRun = (Get-Content -LiteralPath $soakRunPath -Raw).Trim()
    $raw = Invoke-SoakAdb ($soakPrefix + @('exec-out', 'run-as', 'dev.example.ebookviewer.p0', 'cat', 'cache/p3-reading-soak-progress.json'))
    $report = $raw | ConvertFrom-Json
    if ($report.run -cne $expectedRun) { throw 'Stale soak report; not this run' }
    $raw | Set-Content -LiteralPath (Join-Path $soakArtifacts 'reading-soak-report.json') -Encoding utf8
    if ($Mode -eq 'collect') { $raw } else {
        $last = @($report.samples) | Select-Object -Last 1
        [pscustomobject]@{ phase=$report.phase; elapsedSeconds=$report.elapsedSeconds; cycles=$report.cycles; switches=$report.documentSwitches; returns=$report.backgroundReturns; recreations=$report.recreations; sampleCount=@($report.samples).Count; lastPssKiB=$last.pssKiB; lastFdCount=$last.fdCount; cachePeakBytes=$report.cachePeakBytes } | ConvertTo-Json -Compress
    }
    exit
}
$policy = Invoke-SoakAdb ($soakPrefix + @('shell', 'dumpsys', 'window', 'policy'))
$power = Invoke-SoakAdb ($soakPrefix + @('shell', 'dumpsys', 'power'))
if ($policy -match '(?:showing|isStatusBarKeyguard|mShowingLockscreen)=true' -or $power -notmatch '\bmWakefulness=Awake\b') {
    throw 'Device must be awake and unlocked; no workload started'
}
$installed = (Invoke-SoakAdb ($soakPrefix + @('shell', 'pm', 'path', 'dev.example.ebookviewer.p0'))).Trim()
if ($installed -notmatch '^package:(/data/app/[-A-Za-z0-9_~+=./]+/base\.apk)$') { throw 'Expected app installation missing' }
$installedPath = $Matches[1]
$hash = (Invoke-SoakAdb ($soakPrefix + @('shell', 'sha256sum', $installedPath))).Trim()
$hostHash = (Get-FileHash (Join-Path $soakRoot 'app/build/outputs/apk/debug/app-debug.apk') -Algorithm SHA256).Hash
if ($hash -notmatch '^([a-fA-F0-9]{64})\s' -or $Matches[1] -ine $hostHash) { throw 'Installed app differs from validated host APK' }
"Authorized unlocked API 33 device; app SHA-256 $($hostHash.ToLowerInvariant()); identifier omitted"
if ($Mode -eq 'preflight') { exit }
if ($Mode -eq 'install-test') {
    Invoke-SoakAdb ($soakPrefix + @('install', '--no-streaming', '-r', '-t', (Join-Path $soakRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))) 300
}
$installedTest = (Invoke-SoakAdb ($soakPrefix + @('shell', 'pm', 'path', 'dev.example.ebookviewer.p0.test'))).Trim()
if ($installedTest -notmatch '^package:(/data/app/[-A-Za-z0-9_~+=./]+/base\.apk)$') { throw 'Expected instrumentation installation missing' }
$installedTestPath = $Matches[1]
$testHash = (Invoke-SoakAdb ($soakPrefix + @('shell', 'sha256sum', $installedTestPath))).Trim()
$hostTestHash = (Get-FileHash (Join-Path $soakRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') -Algorithm SHA256).Hash
if ($testHash -notmatch '^([a-fA-F0-9]{64})\s' -or $Matches[1] -ine $hostTestHash) { throw 'Installed instrumentation differs from validated host APK' }
"Instrumentation SHA-256 $($hostTestHash.ToLowerInvariant()) verified"
if ($Mode -eq 'install-test') { exit }
New-Item -ItemType Directory -Path $soakArtifacts -Force | Out-Null
$runId = [Guid]::NewGuid().ToString()
$runId | Set-Content -LiteralPath $soakRunPath -Encoding utf8
try {
    $result = Invoke-SoakAdb ($soakPrefix + @('shell', 'am', 'instrument', '-w', '-e', 'suite', 'readingSoak', '-e', 'soakRun', $runId, '-e', 'deadlineSeconds', '1320', 'dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation')) 1380
    $result | Set-Content -LiteralPath (Join-Path $soakArtifacts 'reading-soak-result.txt') -Encoding utf8
    $result
    if ($result -notmatch 'P0 tests=1, failures=0' -or $result -match 'p0DeadlineExceeded=true') { throw 'Soak incomplete or failed' }
} finally {
    try {
        try { Invoke-SoakAdb ($soakPrefix + @('shell', 'am', 'force-stop', 'dev.example.ebookviewer.p0.test')) | Out-Null }
        finally { Invoke-SoakAdb ($soakPrefix + @('shell', 'am', 'force-stop', 'dev.example.ebookviewer.p0')) | Out-Null }
    } finally { Invoke-SoakAdb @('kill-server') | Out-Null }
}
