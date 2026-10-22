param(
    [switch]$CreateKey,
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.android/ebook-viewer-release.p12'),
    [Security.SecureString]$Password,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$releaseRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'android-env.ps1')
$releaseKey = [IO.Path]::GetFullPath($KeystorePath)
$rootPrefix = [IO.Path]::GetFullPath($releaseRoot).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if ($releaseKey.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep the signing key outside the repository.'
}
if ([IO.Path]::GetExtension($releaseKey) -ine '.p12') { throw 'Use a PKCS12 .p12 key file.' }
if ($CreateKey -and (Test-Path -LiteralPath $releaseKey)) { throw 'Key already exists; it will not be overwritten.' }
if (-not $CreateKey -and -not (Test-Path -LiteralPath $releaseKey -PathType Leaf)) {
    throw 'Key is missing. Use -CreateKey only for the first build.'
}
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $releaseRoot 'local-artifacts/releases' }
$releaseOutput = [IO.Path]::GetFullPath($OutputDirectory)

function Convert-ReleaseSecret([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$ownsPassword = -not $PSBoundParameters.ContainsKey('Password')
$confirmation = $null
if ($ownsPassword) { $Password = Read-Host 'Signing password (12+ characters; never send it in chat)' -AsSecureString }
if ($null -eq $Password -or $Password.Length -lt 12) { throw 'Signing password must contain at least 12 characters.' }
$previousEnvironment = @{}
$pendingKey = $null
$plainPassword = $null
Push-Location $releaseRoot
try {
    $plainPassword = Convert-ReleaseSecret $Password
    if ($CreateKey -and $ownsPassword) {
        $confirmation = Read-Host 'Confirm signing password' -AsSecureString
        if ($plainPassword -cne (Convert-ReleaseSecret $confirmation)) { throw 'Passwords do not match.' }
    }
    $releaseEnvironment = @{
        EBOOK_RELEASE_STORE_FILE = $releaseKey
        EBOOK_RELEASE_STORE_PASSWORD = $plainPassword
        EBOOK_RELEASE_KEY_ALIAS = 'ebook-viewer'
        EBOOK_RELEASE_KEY_PASSWORD = $plainPassword
    }
    foreach ($entry in $releaseEnvironment.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    $keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
    if ($CreateKey) {
        $keyDirectory = Split-Path $releaseKey -Parent
        New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
        $pendingKey = Join-Path $keyDirectory ([Guid]::NewGuid().ToString('N') + '.pending.p12')
        & $keytool -genkeypair -keystore $pendingKey -storetype PKCS12 -alias ebook-viewer `
            -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=Ebook Viewer' `
            -storepass:env EBOOK_RELEASE_STORE_PASSWORD -keypass:env EBOOK_RELEASE_KEY_PASSWORD *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed; no release build started.' }
        # Move without replacement: a concurrently created destination must also remain intact.
        [IO.File]::Move($pendingKey, $releaseKey)
        $pendingKey = $null
    }
    & $keytool -list -keystore $releaseKey -storetype PKCS12 -alias ebook-viewer `
        -storepass:env EBOOK_RELEASE_STORE_PASSWORD *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Key/password/alias verification failed; no release build started.' }

    & ./gradlew.bat :app:assembleRelease :app:lintRelease -PreleaseSigning=true `
        --dependency-verification strict --no-daemon --no-configuration-cache `
        '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Signed release build failed; no artifact exported.' }
    $apk = Join-Path $releaseRoot 'app/build/outputs/apk/release/app-release.apk'
    $buildTools = Join-Path $env:ANDROID_HOME 'build-tools/34.0.0'
    $signature = & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed; no artifact exported.' }
    $badging = (& (Join-Path $buildTools 'aapt2.exe') dump badging $apk | Out-String)
    if ($LASTEXITCODE -ne 0 -or $badging -match 'application-debuggable') {
        throw 'Release APK inspection failed; no artifact exported.'
    }
    if ($badging -notmatch "package: name='dev.example.ebookviewer' versionCode='(\d+)' versionName='([0-9]+\.[0-9]+\.[0-9]+)'" ) {
        throw 'Unexpected release identity/version; no artifact exported.'
    }
    $versionCode = $Matches[1]
    $versionName = $Matches[2]
    $destination = Join-Path $releaseOutput "ebook-viewer-$versionName-vc$versionCode.apk"
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    New-Item -ItemType Directory -Force -Path $releaseOutput | Out-Null
    if (Test-Path -LiteralPath $destination) {
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
            throw 'A different APK already exists at this version. Increase versionCode; do not overwrite it.'
        }
    } else {
        # Keep a previous export even if another process creates it concurrently.
        [IO.File]::Copy($apk, $destination, $false)
    }
    Write-Host "Signed APK: $destination"
    Write-Host "SHA-256: $hash"
    Write-Host 'Back up the .p12 key and password separately. This script does not install or publish the APK.'
} finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            Remove-Item -LiteralPath "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
    }
    if ($pendingKey -and (Test-Path -LiteralPath $pendingKey)) { Remove-Item -LiteralPath $pendingKey }
    $plainPassword = $null
    $releaseEnvironment = $null
    if ($confirmation) { $confirmation.Dispose() }
    if ($ownsPassword -and $Password) { $Password.Dispose() }
    Pop-Location
}
