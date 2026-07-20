[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$memoryText = (& docker info --format "{{.MemTotal}}" 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $memoryText) {
    throw "Docker Desktop nao esta disponivel. Inicie-o antes de executar mvn verify -Presilience."
}

[long]$dockerMemory = $memoryText.Trim()
$minimumMemory = 6GB
if ($dockerMemory -lt $minimumMemory) {
    $availableGiB = [Math]::Round($dockerMemory / 1GB, 1)
    throw "O Docker possui somente $availableGiB GiB. Reserve pelo menos 6 GiB para os testes de resiliencia."
}

$mainContainers = @(& docker ps --filter "label=com.docker.compose.project=quarkus-trip" --format "{{.Names}}")
if ($mainContainers.Count -gt 0 -and $mainContainers[0]) {
    throw "A stack normal esta em execucao. Para liberar memoria, rode 'docker compose --profile core --profile full --profile observability --profile metrics down' e tente novamente. O runner nao encerra seus conteineres automaticamente."
}

$freeSpace = (Get-Item -LiteralPath $PSScriptRoot).PSDrive.Free
if ($freeSpace -lt 4GB) {
    throw "Ha menos de 4 GiB livres no disco. Libere espaco antes de iniciar a infraestrutura isolada."
}

Write-Host "Preflight concluido: Docker, memoria e disco prontos para a suite isolada."
