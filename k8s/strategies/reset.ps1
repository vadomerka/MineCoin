$ErrorActionPreference = "Stop"
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
    $global:LASTEXITCODE = 0
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "Шаг '$Title' завершился с кодом $LASTEXITCODE"
    }
}

Invoke-Step "Маршрут finance: весь трафик на finance-service" {
    kubectl apply -f k8s/strategies/blue-green/route-green.yml
}

Invoke-Step "Удаление finance-service-v2" {
    kubectl delete -f k8s/strategies/finance-service-v2.yml --ignore-not-found
}

Invoke-Step "finance-service на версии 1.0.0" {
    kubectl apply -f k8s/32_finance_service.yml
    kubectl rollout status deployment/finance-service -n minecoin --timeout=300s
}

Invoke-Step "Состояние" {
    kubectl get pods -n minecoin -l "app in (finance-service,finance-service-v2)"
    kubectl get httproute finance -n minecoin
}
