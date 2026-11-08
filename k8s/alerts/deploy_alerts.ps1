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

function Get-Secret([string]$Name) {
    $line = Get-Content "k8s/secrets.env" -Encoding UTF8 | Where-Object { $_ -like "$Name=*" } | Select-Object -First 1
    if (-not $line) {
        throw "В k8s/secrets.env нет значения $Name"
    }
    return $line.Substring($Name.Length + 1).Trim()
}

$token = Get-Secret "TELEGRAM_BOT_TOKEN"
$chatId = Get-Secret "TELEGRAM_CHAT_ID"
if ($chatId -notmatch '^-?\d+$') {
    throw "TELEGRAM_CHAT_ID должен быть числом"
}

Invoke-Step "VictoriaMetrics" {
    helm upgrade --install vm-single vm/victoria-metrics-single -n monitoring --create-namespace -f k8s/monitoring/vmsingle-values.yaml
}

Invoke-Step "ConfigMap с правилами алертов" {
    kubectl delete configmap vmalert-rules -n monitoring --ignore-not-found
    kubectl create configmap vmalert-rules -n monitoring --from-file=alert-rules.yaml=k8s/alerts/alert-rules.yaml
}

Invoke-Step "Secret с конфигурацией Alertmanager" {
    $config = [IO.File]::ReadAllText("$PWD/k8s/alerts/alertmanager.yaml").Replace('${TELEGRAM_BOT_TOKEN}', $token).Replace('${TELEGRAM_CHAT_ID}', $chatId)
    $file = New-TemporaryFile
    try {
        [IO.File]::WriteAllText($file.FullName, $config, (New-Object Text.UTF8Encoding($false)))
        kubectl delete secret alertmanager-config -n monitoring --ignore-not-found
        kubectl create secret generic alertmanager-config -n monitoring "--from-file=alertmanager.yaml=$($file.FullName)"
    } finally {
        Remove-Item $file.FullName -Force
    }
}

Invoke-Step "vmalert и Alertmanager" {
    helm upgrade --install vmalert vm/victoria-metrics-alert -n monitoring -f k8s/alerts/vmalert-values.yaml
    kubectl rollout restart deployment/vmalert deployment/alertmanager -n monitoring
}

foreach ($deployment in "vmalert", "alertmanager") {
    Invoke-Step "Ожидание $deployment" {
        kubectl rollout status deployment/$deployment -n monitoring --timeout=180s
    }
}

Invoke-Step "Состояние подов" {
    kubectl get pods -n monitoring
}
