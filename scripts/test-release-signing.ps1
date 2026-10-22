$ErrorActionPreference = 'Stop'
$signingTestRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'android-env.ps1')
$runId = [Guid]::NewGuid().ToString('N')
$keyDirectory = Join-Path ([IO.Path]::GetTempPath()) "ebook-viewer-signing-$runId"
$testKey = Join-Path $keyDirectory 'verification-only.p12'
$testOutput = Join-Path $signingTestRoot "local-artifacts/release-signing-verification/$runId"
$builder = Join-Path $PSScriptRoot 'build-personal-release.ps1'
$password = ConvertTo-SecureString -AsPlainText -Force -String (
    [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
)
$wrongPassword = ConvertTo-SecureString -AsPlainText -Force -String (
    [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
)
$environmentBefore = @{}
foreach ($name in @('STORE_FILE','STORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD')) {
    $fullName = "EBOOK_RELEASE_$name"
    $environmentBefore[$fullName] = [Environment]::GetEnvironmentVariable($fullName, 'Process')
}

function Expect-SigningFailure([scriptblock]$Action, [string]$Message) {
    $failed = $false
    try { & $Action } catch {
        if (-not $_.Exception.Message.Contains($Message)) { throw }
        $failed = $true
    }
    if (-not $failed) { throw "Expected failure was not observed: $Message" }
}

Push-Location $signingTestRoot
try {
    & $builder -CreateKey -KeystorePath $testKey -Password $password -OutputDirectory $testOutput
    $keyHash = (Get-FileHash -LiteralPath $testKey).Hash
    Expect-SigningFailure { & $builder -CreateKey -KeystorePath $testKey -Password $password } 'Key already exists'
    if ((Get-FileHash -LiteralPath $testKey).Hash -ne $keyHash) { throw 'Existing key was modified.' }
    Expect-SigningFailure { & $builder -KeystorePath $testKey -Password $wrongPassword } 'Key/password/alias verification failed'
    Expect-SigningFailure {
        & $builder -CreateKey -KeystorePath (Join-Path $signingTestRoot 'local-artifacts/forbidden.p12') -Password $password
    } 'outside the repository'
    foreach ($entry in $environmentBefore.GetEnumerator()) {
        if ([Environment]::GetEnvironmentVariable($entry.Key, 'Process') -cne $entry.Value) {
            throw 'Signing environment was not restored.'
        }
    }

    # Reuse the generated key and confirm an existing export is never silently replaced.
    $export = @(Get-ChildItem -LiteralPath $testOutput -Filter '*.apk')
    if ($export.Count -ne 1) { throw 'Expected one signed verification APK.' }
    $exportHash = (Get-FileHash -LiteralPath $export[0].FullName).Hash
    & $builder -KeystorePath $testKey -Password $password -OutputDirectory $testOutput
    if ((Get-FileHash -LiteralPath $export[0].FullName).Hash -ne $exportHash) { throw 'Existing export changed.' }
    $conflictDirectory = Join-Path $testOutput 'conflict'
    New-Item -ItemType Directory -Path $conflictDirectory | Out-Null
    $conflictFile = Join-Path $conflictDirectory $export[0].Name
    [IO.File]::WriteAllBytes($conflictFile, [byte[]]@(1,2,3))
    $conflictHash = (Get-FileHash -LiteralPath $conflictFile).Hash
    Expect-SigningFailure {
        & $builder -KeystorePath $testKey -Password $password -OutputDirectory $conflictDirectory
    } 'different APK already exists'
    if ((Get-FileHash -LiteralPath $conflictFile).Hash -ne $conflictHash) {
        throw 'Conflicting output changed.'
    }

    foreach ($name in $environmentBefore.Keys) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
    $missing = (& ./gradlew.bat help -PreleaseSigning=true --dependency-verification strict --no-daemon --console=plain 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0 -or $missing -notmatch 'Missing EBOOK_RELEASE_STORE_FILE') {
        throw 'Missing signing credentials did not fail closed.'
    }
    foreach ($entry in $environmentBefore.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            Remove-Item -LiteralPath "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
    }
    # Finish with the normal unsigned artifact, never a disposable signed APK in the default output.
    & ./gradlew.bat :app:assembleRelease --dependency-verification strict --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Unsigned release restoration failed.' }
    & (Join-Path $PSScriptRoot 'check-build-identities.ps1')
    Write-Host 'Signing checks passed: create/reuse/signature/identity, existing key, wrong password, repository path, output conflict, environment cleanup, missing credentials.'
    Write-Host "Verification-only APK retained at $testOutput; its disposable key is deleted. Do not install or distribute it."
} finally {
    foreach ($entry in $environmentBefore.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            Remove-Item -LiteralPath "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
    }
    $password.Dispose()
    $wrongPassword.Dispose()
    # Only exact files/directories created under the unique OS-temp path; never recursive deletion.
    if (Test-Path -LiteralPath $testKey) { Remove-Item -LiteralPath $testKey }
    if (Test-Path -LiteralPath $keyDirectory) { [IO.Directory]::Delete($keyDirectory, $false) }
    Pop-Location
}
exit 0
