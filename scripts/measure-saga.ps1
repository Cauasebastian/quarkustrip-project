[CmdletBinding()]
param(
    [ValidateRange(1, 200)]
    [int]$Samples = 30,
    [ValidateRange(0, 20)]
    [int]$Warmup = 3,
    [ValidateRange(50, 2000)]
    [int]$PollIntervalMs = 200,
    [ValidateRange(5, 120)]
    [int]$TimeoutSeconds = 30,
    [string]$GatewayUrl = "http://localhost:18080",
    [string]$KeycloakUrl = "http://localhost:8180",
    [string]$JaegerUrl = "http://localhost:16686",
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "target\performance"
}

function Get-AccessToken([string]$Username, [string]$Password) {
    $response = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakUrl/realms/trip/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ client_id = "trip-gateway"; grant_type = "password"; username = $Username; password = $Password }
    return $response.access_token
}

function Invoke-TripApi([string]$Method, [string]$Path, $Body, [hashtable]$ExtraHeaders) {
    $headers = @{ Authorization = "Bearer $script:token" }
    if ($ExtraHeaders) {
        foreach ($entry in $ExtraHeaders.GetEnumerator()) { $headers[$entry.Key] = $entry.Value }
    }
    $arguments = @{ Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json"
        $arguments.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    try {
        return Invoke-RestMethod @arguments
    } catch {
        $details = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "$Method $Path failed: $details"
    }
}

function Get-NearestRank([double[]]$Values, [double]$Percentile) {
    if ($Values.Count -eq 0) { return 0 }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Max(0, [Math]::Ceiling($Percentile * $sorted.Count) - 1)
    return [Math]::Round($sorted[$index], 2)
}

function Wait-Booking([string]$BookingId) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $booking = Invoke-TripApi "GET" "/api/v1/bookings/$BookingId" $null $null
        if ($booking.status -in @("CONFIRMED", "CANCELLED", "FAILED", "MANUAL_REVIEW")) { return $booking }
        Start-Sleep -Milliseconds $PollIntervalMs
    } while ((Get-Date) -lt $deadline)
    throw "Booking $BookingId did not reach a terminal state in $TimeoutSeconds seconds."
}

function Get-TraceMetrics([string]$BookingId) {
    $deadline = (Get-Date).AddSeconds(10)
    $latest = $null
    do {
        try {
            $summary = Invoke-TripApi "GET" "/api/v1/bookings/$BookingId/observability" $null $null
            if ($summary.available -and $summary.primaryTraceId) {
                $trace = Invoke-RestMethod -Uri "$JaegerUrl/api/traces/$($summary.primaryTraceId)"
                $waits = @()
                foreach ($span in $trace.data.spans) {
                    foreach ($tag in $span.tags) {
                        if ($tag.key -eq "outbox.wait_ms") { $waits += [double]$tag.value }
                    }
                }
                $latest = [pscustomobject]@{
                    TraceId = $summary.primaryTraceId
                    Complete = [bool]$summary.complete
                    OutboxWaitP95Ms = if ($waits.Count) { Get-NearestRank $waits 0.95 } else { $null }
                    RetryCount = [int]$summary.signals.retryCount
                    DlqCount = [int]$summary.signals.dlqCount
                    Stages = $summary.stages
                }
                if ($latest.Complete) { return $latest }
            }
        } catch {
            # Jaeger is optional for the functional duration. Retry while its batch exporter flushes.
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    if ($null -ne $latest) { return $latest }
    return [pscustomobject]@{ TraceId = $null; Complete = $false; OutboxWaitP95Ms = $null; RetryCount = 0; DlqCount = 0; Stages = @() }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$script:token = Get-AccessToken "admin" "admin"
$runId = (Get-Date -Format "yyyyMMddHHmmss") + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
$totalRuns = $Warmup + $Samples
$departure = [DateTimeOffset]::UtcNow.AddDays(180).AddMinutes(1)
$arrival = $departure.AddHours(2)
$futureStay = [DateTimeOffset]::UtcNow.AddDays(210)
$baseStay = [DateTimeOffset]::new($futureStay.Year, $futureStay.Month, $futureStay.Day, 0, 0, 0, [TimeSpan]::Zero)

Write-Host "Creating isolated catalog for run $runId..." -ForegroundColor Cyan
$flight = Invoke-TripApi "POST" "/api/v1/catalog/flights" @{
    flightNumber = "PF$($runId.Substring($runId.Length - 8))"
    origin = "FOR"
    destination = "GRU"
    departureTime = $departure.ToString("o")
    arrivalTime = $arrival.ToString("o")
    totalSeats = $totalRuns
    seatPrice = @{ currency = "BRL"; amountMinor = 50000 }
} $null
if ($flight.availableSeats.Count -lt $totalRuns) { throw "Flight did not expose $totalRuns seats." }

$hotel = Invoke-TripApi "POST" "/api/v1/catalog/hotels" @{
    name = "Performance Hotel $runId"
    address = "Latency Avenue 1"
    city = "Fortaleza"
    country = "BR"
    rating = 4
} $null
$room = Invoke-TripApi "POST" "/api/v1/catalog/rooms" @{
    hotelId = $hotel.id
    roomNumber = "P-$($runId.Substring($runId.Length - 6))"
    roomType = "PERFORMANCE"
    nightlyPrice = @{ currency = "BRL"; amountMinor = 20000 }
} $null
$transport = Invoke-TripApi "POST" "/api/v1/catalog/transports" @{
    transportType = "CAR_RENTAL"
    providerName = "Performance Transport $runId"
    vehicleDetailsJson = "{`"runId`":`"$runId`",`"model`":`"Test Car`"}"
    price = @{ currency = "BRL"; amountMinor = 10000 }
} $null

$results = @()
for ($index = 0; $index -lt $totalRuns; $index++) {
    # The local rate limiter keys by token hash. Rotate the in-memory token outside the timer
    # so 30 samples measure the Saga instead of the benchmark client's request budget.
    $script:token = Get-AccessToken "admin" "admin"
    $checkIn = $baseStay.AddDays($index * 3)
    $checkOut = $checkIn.AddDays(2)
    $startsAt = $checkIn.AddHours(9)
    $endsAt = $checkOut.AddHours(9)
    $body = @{
        currency = "BRL"
        paymentMethodRef = "pm_test_success"
        items = @(
            @{ type = "FLIGHT"; resourceId = $flight.id; seatNumber = $flight.availableSeats[$index] },
            @{ type = "HOTEL"; resourceId = $room.id; checkIn = $checkIn.ToString("yyyy-MM-dd"); checkOut = $checkOut.ToString("yyyy-MM-dd") },
            @{ type = "TRANSPORT"; resourceId = $transport.id; startsAt = $startsAt.ToString("o"); endsAt = $endsAt.ToString("o") }
        )
    }

    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $created = Invoke-TripApi "POST" "/api/v1/bookings" $body @{ "Idempotency-Key" = [Guid]::NewGuid().ToString() }
    $booking = Wait-Booking $created.bookingId
    $stopwatch.Stop()
    $serverDuration = ([DateTimeOffset]$booking.updatedAt - [DateTimeOffset]$booking.createdAt).TotalMilliseconds
    $trace = Get-TraceMetrics $booking.id
    $isWarmup = $index -lt $Warmup
    $result = [pscustomobject][ordered]@{
        Run = $index + 1
        Warmup = $isWarmup
        BookingId = $booking.id
        Status = $booking.status
        ObservedDurationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
        ServerDurationMs = [Math]::Round($serverDuration, 2)
        OutboxWaitP95Ms = $trace.OutboxWaitP95Ms
        TraceId = $trace.TraceId
        TraceComplete = $trace.Complete
        RetryCount = $trace.RetryCount
        DlqCount = $trace.DlqCount
    }
    $results += $result
    $kind = if ($isWarmup) { "warmup" } else { "sample" }
    Write-Host ("[{0}/{1}] {2} {3}: {4} ms" -f ($index + 1), $totalRuns, $kind, $booking.status, $result.ObservedDurationMs)
}

$measured = @($results | Where-Object { -not $_.Warmup })
$durations = [double[]]@($measured.ObservedDurationMs)
$outboxWaits = [double[]]@($measured | Where-Object { $null -ne $_.OutboxWaitP95Ms } | ForEach-Object { $_.OutboxWaitP95Ms })
$summary = [pscustomobject][ordered]@{
    Timestamp = [DateTimeOffset]::UtcNow.ToString("o")
    RunId = $runId
    Samples = $Samples
    Warmup = $Warmup
    PollIntervalMs = $PollIntervalMs
    MedianMs = Get-NearestRank $durations 0.50
    P95Ms = Get-NearestRank $durations 0.95
    MaxMs = [Math]::Round(($durations | Measure-Object -Maximum).Maximum, 2)
    OutboxWaitP95Ms = if ($outboxWaits.Count) { Get-NearestRank $outboxWaits 0.95 } else { $null }
    Confirmed = @($measured | Where-Object Status -eq "CONFIRMED").Count
    Failed = @($measured | Where-Object Status -ne "CONFIRMED").Count
    Retries = ($measured | Measure-Object RetryCount -Sum).Sum
    Dlq = ($measured | Measure-Object DlqCount -Sum).Sum
    CompleteTraces = @($measured | Where-Object TraceComplete).Count
    MissingTraces = @($measured | Where-Object { -not $_.TraceId }).Count
    Results = $results
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$jsonPath = Join-Path $OutputDirectory "saga-$stamp.json"
$csvPath = Join-Path $OutputDirectory "saga-$stamp.csv"
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8
$results | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8

Write-Host "Median: $($summary.MedianMs) ms | P95: $($summary.P95Ms) ms | Max: $($summary.MaxMs) ms" -ForegroundColor Cyan
Write-Host "Outbox wait P95: $($summary.OutboxWaitP95Ms) ms"
Write-Host "Reports: $jsonPath and $csvPath"

$accepted = $summary.Failed -eq 0 -and $summary.MedianMs -le 3000 -and $summary.P95Ms -le 5000 `
    -and ($null -eq $summary.OutboxWaitP95Ms -or $summary.OutboxWaitP95Ms -le 250) `
    -and $summary.Retries -eq 0 -and $summary.Dlq -eq 0 -and $summary.CompleteTraces -eq $Samples
if (-not $accepted) {
    throw "Saga latency acceptance criteria were not met. See $jsonPath."
}
