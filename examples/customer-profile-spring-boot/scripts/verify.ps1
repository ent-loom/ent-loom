[CmdletBinding()]
param(
    [string]$MavenRepository = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$python = Get-Command python3 -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command python -ErrorAction SilentlyContinue
}
if (-not $python) {
    throw "请先安装 Python 3.9+，用于运行跨平台验收脚本。"
}
$arguments = @((Join-Path $PSScriptRoot "verify.py"))
if ($MavenRepository) {
    $arguments += @("--maven-repository", $MavenRepository)
}
if ($SkipBuild) {
    $arguments += "--skip-build"
}
& $python.Source @arguments
if ($LASTEXITCODE -ne 0) {
    throw "示例验收失败，退出码：$LASTEXITCODE"
}
