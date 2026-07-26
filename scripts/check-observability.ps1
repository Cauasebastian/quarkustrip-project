param(
    [ValidateSet("core", "full")]
    [string]$Profile = "full"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$composeFiles = @(
    "-f", (Join-Path $root "docker-compose.yml"),
    "-f", (Join-Path $root "docker-compose.observability.yml")
)
$services = @(
    "api-gateway-service",
    "booking-service",
    "flight-service",
    "payment-service"
)
if ($Profile -eq "full") {
    $services += @(
        "hotel-service",
        "transport-service",
        "notification-service",
        "user-service"
    )
}

$errors = @()
foreach ($service in $services) {
    $containerId = ((& docker compose @composeFiles --profile $Profile --profile observability ps -q $service) | Out-String).Trim()
    if (-not $containerId) {
        $errors += "$service nao esta em execucao"
        continue
    }

    $environment = & docker inspect --format "{{range .Config.Env}}{{println .}}{{end}}" $containerId
    $values = @{}
    foreach ($entry in $environment) {
        $parts = $entry -split "=", 2
        if ($parts.Length -eq 2) {
            $values[$parts[0]] = $parts[1]
        }
    }

    if ($values["QUARKUS_OTEL_SDK_DISABLED"] -ne "false") {
        $errors += "$service esta com o SDK OpenTelemetry desabilitado"
    }
    if ($values["QUARKUS_OTEL_INSTRUMENT_VERTX_SQL_CLIENT"] -ne "false") {
        $errors += "$service ainda esta exportando spans SQL"
    }
    if ($values["OTEL_EXPORTER_OTLP_ENDPOINT"] -ne "http://jaeger:4317") {
        $errors += "$service nao aponta para o OTLP do Jaeger"
    }
}

if ($errors.Count -gt 0) {
    Write-Host "Configuracao de observabilidade inconsistente:" -ForegroundColor Red
    $errors | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

try {
    $response = Invoke-RestMethod -Uri "http://localhost:16686/api/services" -TimeoutSec 5
    $observed = @($response.data | Where-Object { $_ -ne "jaeger" } | Sort-Object)
    Write-Host "OpenTelemetry esta uniforme em todos os servicos ativos." -ForegroundColor Green
    if ($observed.Count -eq 0) {
        Write-Host "O Jaeger ainda nao recebeu spans. Gere uma nova reserva para popular o trace."
    } else {
        Write-Host "Servicos ja observados no Jaeger: $($observed -join ', ')"
    }
} catch {
    Write-Host "Os containers estao configurados, mas a API do Jaeger ainda nao respondeu." -ForegroundColor Yellow
    exit 1
}
