param([string]$Serial, [switch]$FeatureChecks, [switch]$ImageRenderChecks)

$ErrorActionPreference = 'Stop'
if ($FeatureChecks -and $ImageRenderChecks) { throw 'Choose one execution probe at a time.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'android-env.ps1')
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$deviceOutput = & $adb devices
if ($LASTEXITCODE -ne 0) { throw 'adb could not enumerate devices.' }
$devices = @($deviceOutput | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) {
    if ($Serial -notin $devices) { throw 'The selected device is not connected and authorized.' }
} elseif ($devices.Count -eq 1) {
    $Serial = $devices[0]
} else {
    throw 'Connect one authorized device or choose one with -Serial; do not publish identifiers.'
}
$deviceApi = & $adb -s $Serial shell getprop ro.build.version.sdk
if ($LASTEXITCODE -ne 0 -or [int]($deviceApi | Select-Object -Last 1) -lt 30) {
    throw 'These standalone SDK extension probes require Android API 30 or newer.'
}

$outputRoot = Join-Path $projectRoot 'local-artifacts/p2-platform-probe'
$classesRoot = Join-Path $outputRoot 'classes'
$dexRoot = Join-Path $outputRoot 'dex'
[System.IO.Directory]::CreateDirectory($classesRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($dexRoot) | Out-Null
$androidJar = Join-Path $env:ANDROID_HOME 'platforms/android-35/android.jar'
$sources = @((Join-Path $PSScriptRoot 'probes/PdfCapabilities.java'))
$classFiles = @((Join-Path $classesRoot 'PdfCapabilities.class'))
if ($FeatureChecks) {
    $sources += Join-Path $PSScriptRoot 'probes/PdfFeatureProbe.java'
    $classFiles += Join-Path $classesRoot 'PdfFeatureProbe.class'
}
if ($ImageRenderChecks) {
    $sources += Join-Path $PSScriptRoot 'probes/PdfImageRenderProbe.java'
    $classFiles += Join-Path $classesRoot 'PdfImageRenderProbe.class'
}
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -classpath $androidJar -d $classesRoot @sources
if ($LASTEXITCODE -ne 0) { throw 'Platform probe Java compilation failed.' }
& (Join-Path $env:ANDROID_HOME 'build-tools/34.0.0/d8.bat') --min-api 30 --lib $androidJar --output $dexRoot @classFiles
if ($LASTEXITCODE -ne 0) { throw 'Platform probe DEX compilation failed.' }

# This shell-process probe neither installs an APK nor starts/force-stops the reader.
$probeId = [Guid]::NewGuid().ToString('N')
$deviceDex = "/data/local/tmp/ebook-viewer-pdf-capabilities-$probeId.dex"
$deviceFixtures = "/data/local/tmp/ebook-viewer-p2-fixtures-$probeId"
$copiedFixtures = @()
$fixtureDirectoryCreated = $false
& $adb -s $Serial push (Join-Path $dexRoot 'classes.dex') $deviceDex | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Platform probe transfer failed.' }
try {
    if ($FeatureChecks -or $ImageRenderChecks) {
        $fixturesRoot = Join-Path $projectRoot 'local-artifacts/p2-fixtures'
        $manifest = Get-Content (Join-Path $fixturesRoot 'manifest.json') -Raw | ConvertFrom-Json
        & $adb -s $Serial shell mkdir $deviceFixtures
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic fixture directory creation failed.' }
        $fixtureDirectoryCreated = $true
        $fixtureNames = if ($FeatureChecks) { @('text-outline.pdf', 'aes128.pdf', 'aes256.pdf') } else {
            @('gray-q85.pdf', 'rgb-q85.pdf', 'rgb-q50.pdf', 'gray-half-q85.pdf')
        }
        foreach ($fixtureName in $fixtureNames) {
            $localFixture = Join-Path $fixturesRoot $fixtureName
            $expected = @($manifest.files | Where-Object { $_.file -eq $fixtureName })
            if ($expected.Count -ne 1 -or (Get-FileHash $localFixture -Algorithm SHA256).Hash -ne $expected[0].sha256) {
                throw 'Synthetic fixture manifest/hash mismatch.'
            }
            $deviceFile = "$deviceFixtures/$fixtureName"
            & $adb -s $Serial push $localFixture $deviceFile | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Synthetic fixture transfer failed.' }
            $copiedFixtures += $deviceFile
        }
        $mainClass = if ($FeatureChecks) { 'PdfFeatureProbe' } else { 'PdfImageRenderProbe' }
        $result = & $adb -s $Serial shell "CLASSPATH=$deviceDex app_process /system/bin $mainClass $deviceFixtures"
    } else {
        $result = & $adb -s $Serial shell "CLASSPATH=$deviceDex app_process /system/bin PdfCapabilities"
    }
    if ($LASTEXITCODE -ne 0) { throw 'Platform probe execution failed.' }
    $report = $result | ConvertFrom-Json
    $report | Add-Member -NotePropertyName probeId -NotePropertyValue $probeId
    $report | Add-Member -NotePropertyName completedAtUtc -NotePropertyValue ([DateTime]::UtcNow.ToString('o'))
    if ($FeatureChecks) {
        if ($report.passed -ne $true) { throw 'Synthetic platform checks did not pass.' }
        $reportFile = 'features.json'
    } elseif ($ImageRenderChecks) {
        if ($report.runs.Count -ne 24) { throw 'Incomplete isolated image render report.' }
        $reportFile = 'image-render.json'
    } else {
        if (-not $report.api -or $report.classes.Count -ne 5) { throw 'Incomplete platform inventory.' }
        $reportFile = 'capabilities.json'
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $outputRoot $reportFile) -Encoding utf8
    $report | ConvertTo-Json -Depth 8
} finally {
    foreach ($deviceFile in $copiedFixtures) {
        & $adb -s $Serial shell rm -f $deviceFile | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Synthetic fixture cleanup failed.' }
    }
    if ($fixtureDirectoryCreated) {
        & $adb -s $Serial shell rmdir $deviceFixtures | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Synthetic fixture directory cleanup failed.' }
    }
    & $adb -s $Serial shell rm -f $deviceDex | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Probe cleanup failed; only the public-API probe DEX remains.' }
}
