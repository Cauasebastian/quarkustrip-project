[CmdletBinding()]
param(
    [ValidateSet("core", "full")]
    [string]$Profile = "core",

    [ValidateSet("jvm", "native")]
    [string]$Mode = "jvm",

    [switch]$Observability,
    [switch]$Metrics,
    [switch]$NoBuild,

    [int]$TimeoutSeconds = 240,
    [int]$StabilizationSeconds = 60,
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "target\performance"
}

function ConvertTo-MiB([string]$Value) {
    if ($Value -notmatch '^\s*([0-9.]+)\s*([KMGT]?i?B)\s*$') {
        return 0.0
    }

    $number = [double]$matches[1]
    switch ($matches[2]) {
        "B"   { return $number / 1MB }
        "KB"  { return $number / 1024 }
        "KiB" { return $number / 1024 }
        "MB"  { return $number }
        "MiB" { return $number }
        "GB"  { return $number * 1024 }
        "GiB" { return $number * 1024 }
        "TB"  { return $number * 1024 * 1024 }
        "TiB" { return $number * 1024 * 1024 }
        default { return 0.0 }
    }
}

function Invoke-Compose([string[]]$Arguments) {
    & docker @script:composeBase @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose falhou: $($Arguments -join ' ')"
    }
}

$composeBase = @("compose", "-f", (Join-Path $repoRoot "docker-compose.yml"))
if ($Mode -eq "native") {
    $composeBase += @("-f", (Join-Path $repoRoot "docker-compose.native.yml"))
    foreach ($module in @("api-gateway-service", "booking-service", "flight-service", "payment-service", "notification-service")) {
        $runner = @(Get-ChildItem -Path (Join-Path $repoRoot "$module\target\*-runner") -ErrorAction SilentlyContinue)
        if ($runner.Count -eq 0) {
            throw "Binario nativo ausente para $module. Execute 'mvn verify -Pnative' primeiro."
        }
    }
}
if ($Observability) {
    $composeBase += @("-f", (Join-Path $repoRoot "docker-compose.observability.yml"))
}

$profileArguments = @("--profile", $Profile)
if ($Observability) { $profileArguments += @("--profile", "observability") }
if ($Metrics) { $profileArguments += @("--profile", "metrics") }

Push-Location $repoRoot
try {
    Invoke-Compose @("down", "--remove-orphans")

    $upArguments = $profileArguments + @("up", "-d")
    if (-not $NoBuild) { $upArguments += "--build" }

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-Compose $upArguments

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $containerIds = @()
    do {
        $containerIds = @(& docker @composeBase @profileArguments ps -q) | Where-Object { $_ }
        $states = @()
        foreach ($containerId in $containerIds) {
            $states += docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId
        }

        $unhealthy = @($states | Where-Object { $_ -in @("unhealthy", "exited", "dead") })
        if ($unhealthy.Count -gt 0) {
            Invoke-Compose ($profileArguments + @("ps"))
            throw "Um ou mais conteineres falharam durante a inicializacao."
        }

        $allHealthy = $containerIds.Count -gt 0 -and @($states | Where-Object { $_ -ne "healthy" }).Count -eq 0
        if (-not $allHealthy) { Start-Sleep -Seconds 1 }
    } until ($allHealthy -or (Get-Date) -ge $deadline)

    if (-not $allHealthy) {
        Invoke-Compose ($profileArguments + @("ps"))
        throw "A stack nao ficou saudavel em $TimeoutSeconds segundos."
    }

    $stopwatch.Stop()
    $healthySeconds = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2)
    Write-Host "Stack saudavel em $healthySeconds segundos. Aguardando estabilizacao..." -ForegroundColor Green
    if ($StabilizationSeconds -gt 0) { Start-Sleep -Seconds $StabilizationSeconds }

    $statistics = @()
    $statsLines = @(& docker stats --no-stream --format '{{json .}}' @containerIds)
    foreach ($line in $statsLines) {
        if (-not $line) { continue }
        $item = $line | ConvertFrom-Json
        $usedMemory = ($item.MemUsage -split '/')[0].Trim()
        $statistics += [pscustomobject][ordered]@{
            Name = $item.Name
            MemoryUsage = $usedMemory
            MemoryMiB = [math]::Round((ConvertTo-MiB $usedMemory), 2)
            MemoryPercent = $item.MemPerc
            CpuPercent = $item.CPUPerc
            Pids = $item.PIDs
        }
    }

    $totalMemoryMiB = [math]::Round((($statistics | Measure-Object -Property MemoryMiB -Sum).Sum), 2)
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

    $report = [pscustomobject][ordered]@{
        Timestamp = (Get-Date).ToUniversalTime().ToString("o")
        Profile = $Profile
        Mode = $Mode
        Observability = [bool]$Observability
        Metrics = [bool]$Metrics
        HealthySeconds = $healthySeconds
        StabilizationSeconds = $StabilizationSeconds
        TotalMemoryMiB = $totalMemoryMiB
        Containers = $statistics
    }

    $jsonPath = Join-Path $OutputDirectory "compose-$Profile-$Mode-$timestamp.json"
    $csvPath = Join-Path $OutputDirectory "compose-$Profile-$Mode-$timestamp.csv"
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
    $statistics | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

    $statistics | Sort-Object MemoryMiB -Descending | Format-Table -AutoSize
    Write-Host "Memoria total: $totalMemoryMiB MiB" -ForegroundColor Cyan
    Write-Host "Relatorios: $jsonPath e $csvPath"
} finally {
    Pop-Location
}
