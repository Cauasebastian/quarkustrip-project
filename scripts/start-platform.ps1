param(
    [ValidateSet("core", "full")]
    [string]$Profile = "core",
    [switch]$Observability,
    [switch]$Native,
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$arguments = @("compose", "-f", (Join-Path $root "docker-compose.yml"))

if ($Native) {
    $arguments += @("-f", (Join-Path $root "docker-compose.native.yml"))
}
if ($Observability) {
    $arguments += @("-f", (Join-Path $root "docker-compose.observability.yml"))
}

$arguments += @("--profile", $Profile)
if ($Observability) {
    $arguments += @("--profile", "observability")
}
$composeArguments = @($arguments)
$arguments += @("up", "-d", "--wait", "--wait-timeout", "180")
if ($Build) {
    $arguments += "--build"
}

Write-Host "Iniciando Trip Platform ($Profile)..." -ForegroundColor Cyan
& docker @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose terminou com codigo $LASTEXITCODE"
}

if ($Observability) {
    & (Join-Path $PSScriptRoot "check-observability.ps1") -Profile $Profile
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "Jaeger: http://localhost:16686" -ForegroundColor Green
}

Write-Host "UI: http://localhost:3000" -ForegroundColor Green
$gatewayBinding = (& docker @composeArguments port api-gateway-service 8080).Trim()
$gatewayPort = if ($gatewayBinding -match ":(\d+)$") { $Matches[1] } else { "8080" }
Write-Host "Gateway: http://localhost:$gatewayPort" -ForegroundColor Green
