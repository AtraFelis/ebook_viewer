param([switch]$AcceptAndroidSdkLicenses)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$artifactRoot = Join-Path $projectRoot 'local-artifacts'
$downloadRoot = Join-Path $artifactRoot 'bootstrap'
$sdkRoot = Join-Path $artifactRoot 'android-sdk'
$lock = Get-Content (Join-Path $PSScriptRoot 'windows-toolchain.lock.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Force $downloadRoot | Out-Null

foreach ($package in $lock) {
    $archive = Join-Path $downloadRoot $package.file
    if (-not (Test-Path $archive)) {
        Invoke-WebRequest $package.url -OutFile $archive
    }
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $package.sha256) {
        throw "Checksum mismatch: $($package.file). No extraction performed."
    }
    $destination = [IO.Path]::GetFullPath((Join-Path $artifactRoot $package.destination))
    if (-not $destination.StartsWith($artifactRoot + [IO.Path]::DirectorySeparatorChar)) {
        throw 'Destination outside local-artifacts.'
    }
    if (-not (Test-Path $destination)) {
        $staging = Join-Path $downloadRoot ([guid]::NewGuid().ToString())
        Expand-Archive -LiteralPath $archive -DestinationPath $staging
        $source = [IO.Path]::GetFullPath((Join-Path $staging $package.archiveRoot))
        if (-not $source.StartsWith($staging + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Archive source outside staging directory.'
        }
        New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
        Move-Item -LiteralPath $source -Destination $destination
    }
}

. (Join-Path $PSScriptRoot 'android-env.ps1')
$properties = Join-Path $projectRoot 'local.properties'
if (-not (Test-Path $properties)) {
    [IO.File]::WriteAllText($properties, 'sdk.dir=' + $sdkRoot.Replace('\', '/') + "`n")
}
& "$env:JAVA_HOME/bin/java.exe" -version
if ($LASTEXITCODE -ne 0) { throw 'JDK failed.' }
& "$sdkRoot/cmdline-tools/12.0/bin/sdkmanager.bat" "--sdk_root=$sdkRoot" --list_installed
if ($LASTEXITCODE -ne 0) { throw 'SDK inspection failed.' }
if ($AcceptAndroidSdkLicenses) {
    1..20 | ForEach-Object { 'y' } | & "$sdkRoot/cmdline-tools/12.0/bin/sdkmanager.bat" "--sdk_root=$sdkRoot" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'SDK license acceptance failed.' }
} else {
    Write-Host 'Review SDK licenses with sdkmanager --licenses before downloading additional packages.'
}
