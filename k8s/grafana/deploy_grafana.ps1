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

Invoke-Step "Репозиторий чарта Grafana" {
    helm repo add grafana-community https://grafana-community.github.io/helm-charts --force-update
    helm repo update grafana-community
}

Invoke-Step "VictoriaMetrics" {
    helm upgrade --install vm-single vm/victoria-metrics-single -n monitoring --create-namespace -f k8s/monitoring/vmsingle-values.yaml
}

Invoke-Step "ConfigMap с дашбордом MineCoin" {
    kubectl delete configmap grafana-dashboard-minecoin -n monitoring --ignore-not-found
    kubectl create configmap grafana-dashboard-minecoin -n monitoring --from-file=minecoin-dashboard.json=k8s/grafana/minecoin-dashboard.json
}

Invoke-Step "Grafana" {
    helm upgrade --install grafana grafana-community/grafana --version 14.4.1 -n monitoring -f k8s/grafana/grafana-values.yaml
}

Invoke-Step "Ожидание grafana" {
    kubectl rollout status deployment/grafana -n monitoring --timeout=300s
}

Invoke-Step "Состояние подов" {
    kubectl get pods -n monitoring
}

Write-Host ""
Write-Host "Пароль admin: [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String((kubectl get secret grafana -n monitoring -o jsonpath='{.data.admin-password}')))"
Write-Host "Интерфейс:    kubectl port-forward svc/grafana 3000:80 -n monitoring  ->  http://localhost:3000"
