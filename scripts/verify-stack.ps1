$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $components = @{'discovery-service'=8761; 'api-gateway'=8080; 'identity-service'=8081; 'catalog-service'=8082; 'circulation-service'=8083; 'reservation-service'=8084; 'notification-service'=8085}
    foreach ($component in $components.GetEnumerator()) {
        foreach ($probe in @('health', 'health/liveness', 'health/readiness', 'metrics', 'prometheus')) {
            $output = & docker compose exec -T $component.Key curl -fsS --retry 10 --retry-delay 3 --retry-all-errors "http://localhost:$($component.Value)/actuator/$probe"
            if ($LASTEXITCODE -ne 0) { throw "Failed $($component.Key) /actuator/$probe" }
            if ($probe -like 'health*' -and ($output | ConvertFrom-Json).status -ne 'UP') { throw "Unhealthy $($component.Key): $output" }
        }
        Write-Host "PASS health/probes/metrics: $($component.Key)"
    }
    $registry = Invoke-RestMethod 'http://localhost:8761/eureka/apps' -Headers @{Accept='application/json'}
    foreach ($name in $components.Keys | Where-Object { $_ -ne 'discovery-service' }) {
        $app = $registry.applications.application | Where-Object { $_.name -eq $name.ToUpperInvariant() }
        if (-not ($app.instance | Where-Object { $_.status -eq 'UP' })) { throw "Eureka has no UP instance: $name (allow a minute after startup)" }
    }
    Write-Host 'PASS Eureka: all six client services registered UP'
    $redis = & docker compose exec -T redis redis-cli ping
    if ($LASTEXITCODE -ne 0 -or $redis -ne 'PONG') { throw 'Redis PING failed' }
    $topics = & docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list
    if ($LASTEXITCODE -ne 0) { throw 'Kafka list failed' }
    foreach ($topic in @('library.loan.approved.v1','library.loan.returned.v1','library.loan.overdue.v1','library.reservation.ready.v1')) {
        if ($topic -notin $topics) { throw "Missing Kafka topic: $topic" }
    }
    Write-Host 'PASS Redis PING and all four agreed Kafka topics'
    foreach ($prefix in @('identity','catalog','circulation','reservation','notification')) {
        $code = & docker compose exec -T api-gateway curl -sS -o /dev/null -w '%{http_code}' "http://localhost:8080/api/$prefix/__infrastructure_probe"
        if ($LASTEXITCODE -ne 0 -or $code -ne '404') { throw "Gateway route ${prefix}: expected downstream 404 for an absent endpoint, got $code" }
    }
    Write-Host 'PASS Gateway: all five routes resolve through Eureka (downstream absent endpoint returns 404)' 
} finally {
    Pop-Location
}
