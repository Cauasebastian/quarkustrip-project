[CmdletBinding()]
param(
    [double]$MinimumDockerMemoryGiB = 7.5,
    [double]$MinimumFreeDiskGiB = 8.0
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

try {
    $dockerInfo = docker info --format '{{json .}}' | ConvertFrom-Json
} catch {
    Fail "Docker Desktop nao esta acessivel. Inicie-o antes do build nativo."
}

$dockerMemoryGiB = [math]::Round(([double]$dockerInfo.MemTotal / 1GB), 2)
if ($dockerMemoryGiB -lt $MinimumDockerMemoryGiB) {
    Fail "Docker possui $dockerMemoryGiB GiB; configure aproximadamente 8 GB antes do build nativo."
}

if ($dockerInfo.Architecture -notin @("x86_64", "amd64")) {
    Fail "Arquitetura Docker '$($dockerInfo.Architecture)' nao e compativel com o runtime x86_64 configurado."
}

$driveName = (Get-Item -LiteralPath $repoRoot).PSDrive.Name
$freeDiskGiB = [math]::Round(((Get-PSDrive -Name $driveName).Free / 1GB), 2)
if ($freeDiskGiB -lt $MinimumFreeDiskGiB) {
    Fail "A unidade $driveName possui $freeDiskGiB GiB livres; sao necessarios pelo menos $MinimumFreeDiskGiB GiB."
}

$runningContainers = @(
    docker ps `
        --filter "label=com.docker.compose.project=quarkus-trip" `
        --format '{{.Names}}'
) | Where-Object { $_ }

if ($runningContainers.Count -gt 0) {
    $names = $runningContainers -join ", "
    Fail "A stack ainda esta em execucao ($names). Execute docker compose --profile '*' down sem '-v' antes do build nativo."
}

try {
    $null = Get-Command mvn -ErrorAction Stop
} catch {
    Fail "Maven nao foi encontrado no PATH."
}

Write-Host "Pre-requisitos do build nativo atendidos:" -ForegroundColor Green
Write-Host "  Docker: $dockerMemoryGiB GiB, arquitetura $($dockerInfo.Architecture)"
Write-Host "  Disco livre em $driveName`: $freeDiskGiB GiB"
Write-Host "  Stack quarkus-trip: parada; volumes preservados"
Write-Host "Execute: mvn verify -Pnative"
