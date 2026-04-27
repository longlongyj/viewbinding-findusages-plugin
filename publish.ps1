# 运行命令：.\publish.ps1
# 功能：打包 + 签名 + 自动上传到 JetBrains Marketplace（需先手动上传过一次）
# 可选参数：.\publish.ps1 -Channel beta   （默认 stable）
param([string]$Channel = 'stable')
$ErrorActionPreference = 'Stop'

$certDir = Join-Path $PSScriptRoot 'certs'
$privateKeyPath = Join-Path $certDir 'private.pem'
$certChainPath  = Join-Path $certDir 'chain.crt'
if (-not (Test-Path $privateKeyPath) -or -not (Test-Path $certChainPath)) { Write-Error 'Certificate files not found'; exit 1 }
$env:PLUGIN_PRIVATE_KEY    = Get-Content $privateKeyPath -Raw
$env:PLUGIN_CERTIFICATE    = Get-Content $certChainPath -Raw
$env:PLUGIN_KEY_PASSPHRASE = ''

if (-not $env:PLUGIN_PUBLISH_TOKEN) {
    $token = Read-Host 'Enter JetBrains Marketplace Token'
    if ([string]::IsNullOrWhiteSpace($token)) { Write-Error 'Token cannot be empty'; exit 1 }
    $env:PLUGIN_PUBLISH_TOKEN = $token
}
$env:PLUGIN_PUBLISH_CHANNEL = $Channel

Write-Host 'Publishing...' -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" publishPlugin --info
if ($LASTEXITCODE -ne 0) { Write-Error 'Publish failed'; exit 1 }
Write-Host 'Published successfully!' -ForegroundColor Green

$distDir     = Join-Path $PSScriptRoot 'build\distributions'
$releasesDir = Join-Path $PSScriptRoot 'releases'
$zip = Get-ChildItem $distDir -Filter '*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($zip) { Copy-Item $zip.FullName $releasesDir -Force; Write-Host "Copied to releases\$($zip.Name)" -ForegroundColor Cyan }
Write-Host 'Plugin page: https://plugins.jetbrains.com/plugin/YOUR_PLUGIN_ID' -ForegroundColor Yellow
