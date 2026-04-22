# publish.ps1
# 一键签名并发布插件到 JetBrains Marketplace
#
# 发布流程说明：
#   publishPlugin 内部依次执行：
#     1. buildPlugin   → 生成 zip（与本地安装包相同，无需混淆）
#     2. signPlugin    → 用 certs/ 私钥对 zip 签名（Marketplace 要求）
#     3. 上传至 Marketplace 等待审核
#
# 使用前提：
#   1. 已在 https://plugins.jetbrains.com 创建插件页并获取 Token
#   2. certs/ 目录中已有 private.pem 和 chain.crt
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

$env:PLUGIN_PRIVATE_KEY    = Get-Content $privateKeyPath -Raw
$env:PLUGIN_CERTIFICATE    = Get-Content $certChainPath -Raw
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

if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ 发布失败，请检查上方日志"
    exit 1
}

Write-Host "✅ 发布成功！" -ForegroundColor Green

# ── 4. 同步 zip 到 releases/ 目录（便于 Git 留档和离线分发）────────────────
$distDir     = Join-Path $PSScriptRoot "build\distributions"
$releasesDir = Join-Path $PSScriptRoot "releases"
$zip = Get-ChildItem $distDir -Filter "*.zip" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($zip) {
    Copy-Item $zip.FullName $releasesDir -Force
    Write-Host "📦 已同步到 releases\$($zip.Name)" -ForegroundColor Cyan
}

Write-Host "   插件页面：https://plugins.jetbrains.com/plugin/<你的插件ID>" -ForegroundColor Yellow

