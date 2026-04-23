$ErrorActionPreference = 'Stop'

$certDir = Join-Path $PSScriptRoot 'certs'
$privateKeyPath = Join-Path $certDir 'private.pem'
$certChainPath  = Join-Path $certDir 'chain.crt'
if (-not (Test-Path $privateKeyPath) -or -not (Test-Path $certChainPath)) { Write-Error 'Certificate files not found'; exit 1 }
$env:PLUGIN_PRIVATE_KEY    = Get-Content $privateKeyPath -Raw
$env:PLUGIN_CERTIFICATE    = Get-Content $certChainPath -Raw
$env:PLUGIN_KEY_PASSPHRASE = ''

Write-Host 'Building and signing plugin...' -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" buildPlugin signPlugin
if ($LASTEXITCODE -ne 0) { Write-Error 'Build/sign failed'; exit 1 }

$distDir     = Join-Path $PSScriptRoot 'build\distributions'
$releasesDir = Join-Path $PSScriptRoot 'releases'
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }

$zip = Get-ChildItem $distDir -Filter '*-signed.zip' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $zip) {
    $zip = Get-ChildItem $distDir -Filter '*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}
if ($zip) {
    Copy-Item $zip.FullName $releasesDir -Force
    Write-Host "Done! Signed plugin saved to: releases\$($zip.Name)" -ForegroundColor Green
    Write-Host 'Upload it manually at: https://plugins.jetbrains.com/plugin/add' -ForegroundColor Yellow
} else {
    Write-Error 'No zip found in build\distributions'
}

