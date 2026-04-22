# publish.ps1
# 一键签名并发布插件到 JetBrains Marketplace
#
# 使用前提：
#   1. 已在 https://plugins.jetbrains.com 创建插件页并获取 Token
#   2. certs/ 目录中已有 private.pem 和 chain.crt（见 README）
#
# 使用方式：
#   .\publish.ps1
#   或指定渠道（beta）：
#   .\publish.ps1 -Channel beta

param(
    [string]$Channel = "stable"
)

$ErrorActionPreference = "Stop"

# ── 1. 读取证书文件 ──────────────────────────────────────────────────────────
$certDir = Join-Path $PSScriptRoot "certs"
$privateKeyPath = Join-Path $certDir "private.pem"
$certChainPath  = Join-Path $certDir "chain.crt"

if (-not (Test-Path $privateKeyPath) -or -not (Test-Path $certChainPath)) {
    Write-Error "❌ 未找到证书文件，请先生成：$certDir"
    exit 1
}

$env:PLUGIN_PRIVATE_KEY   = Get-Content $privateKeyPath -Raw
$env:PLUGIN_CERTIFICATE   = Get-Content $certChainPath -Raw
$env:PLUGIN_KEY_PASSPHRASE = ""   # 生成证书时未设密码，留空

# ── 2. 读取发布 Token ────────────────────────────────────────────────────────
if (-not $env:PLUGIN_PUBLISH_TOKEN) {
    $token = Read-Host "请输入 JetBrains Marketplace Token（https://plugins.jetbrains.com → 头像 → My Tokens）"
    if ([string]::IsNullOrWhiteSpace($token)) {
        Write-Error "❌ Token 不能为空"
        exit 1
    }
    $env:PLUGIN_PUBLISH_TOKEN = $token
}

$env:PLUGIN_PUBLISH_CHANNEL = $Channel

# ── 3. 构建 + 签名 + 发布 ────────────────────────────────────────────────────
Write-Host "🚀 开始构建并发布到 Marketplace（渠道：$Channel）..." -ForegroundColor Cyan

& "$PSScriptRoot\gradlew.bat" publishPlugin

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 发布成功！" -ForegroundColor Green
    Write-Host "   插件页面：https://plugins.jetbrains.com/plugin/<你的插件ID>" -ForegroundColor Yellow
} else {
    Write-Error "❌ 发布失败，请检查上方日志"
}

