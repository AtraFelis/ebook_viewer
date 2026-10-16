$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = Join-Path $projectRoot 'local-artifacts/toolchains/jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $projectRoot 'local-artifacts/android-sdk'
if (-not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    throw 'Run scripts/bootstrap-windows.ps1 first.'
}
$env:PATH = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:PATH"
