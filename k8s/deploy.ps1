param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$EnvoyGatewayVersion = "v1.10.0"
Set-Location (Split-Path $PSScriptRoot -Parent)

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
    $global:LASTEXITCODE = 0
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "Шаг '$Title' завершился с кодом $LASTEXITCODE"
    }
}

if (-not (Test-Path "k8s/secrets.env")) {
    throw "Нет файла k8s/secrets.env: скопируйте k8s/secrets.env.example и заполните пароли"
}

Invoke-Step "Проверка minikube" {
    minikube status
}

if (-not $SkipBuild) {
    Invoke-Step "Переключение docker на демон minikube" {
        & minikube -p minikube docker-env --shell powershell | Invoke-Expression
    }
    Invoke-Step "Сборка образов" {
        docker compose build user-migrate finance-migrate frontend prober
    }
    foreach ($version in "1.0.0", "2.0.0") {
        Invoke-Step "Образ finance-service:$version" {
            docker build -q -f FinanceService/Dockerfile --build-arg APP_VERSION=$version -t minecoin/finance-service:$version .
        }
    }
    Invoke-Step "Образы в minikube" {
        docker images "minecoin/*" --format "table {{.Repository}}:{{.Tag}}\t{{.CreatedSince}}"
    }
}

Invoke-Step "Namespace" {
    kubectl apply -f k8s/00_namespace.yml
}

Invoke-Step "Secret minecoin-secrets" {
    kubectl delete secret minecoin-secrets -n minecoin --ignore-not-found
    kubectl create secret generic minecoin-secrets -n minecoin --from-env-file=k8s/secrets.env
}

Invoke-Step "ConfigMap postgres-init" {
    kubectl delete configmap postgres-init -n minecoin --ignore-not-found
    kubectl create configmap postgres-init -n minecoin --from-file=infra/postgres/init/01-init.sh
}

Invoke-Step "PostgreSQL" {
    kubectl apply -f k8s/10_postgres.yml
    kubectl rollout status statefulset/postgres -n minecoin --timeout=180s
}

Invoke-Step "Миграции" {
    kubectl delete job user-migrate finance-migrate user-create-admin -n minecoin --ignore-not-found
    kubectl apply -f k8s/20_user_service_config.yml -f k8s/21_user_migrate_job.yml -f k8s/30_finance_service_config.yml -f k8s/31_finance_migrate_job.yml
    kubectl wait --for=condition=Complete job/user-migrate job/finance-migrate -n minecoin --timeout=180s
}

Invoke-Step "Envoy Gateway" {
    kubectl apply --server-side -f "https://github.com/envoyproxy/gateway/releases/download/$EnvoyGatewayVersion/install.yaml" | Out-Null
    kubectl wait --timeout=5m -n envoy-gateway-system deployment/envoy-gateway --for=condition=Available
}

Invoke-Step "Сервисы и создание администратора" {
    kubectl apply -f k8s/22_user_service.yml -f k8s/32_finance_service.yml -f k8s/40_frontend.yml -f k8s/23_user_create_admin_job.yml -f k8s/50_prober.yml -f k8s/60_gateway.yml
    kubectl rollout restart deployment/user-service deployment/finance-service deployment/frontend deployment/prober -n minecoin
}

foreach ($deployment in "user-service", "finance-service", "frontend", "prober") {
    Invoke-Step "Ожидание $deployment" {
        kubectl rollout status deployment/$deployment -n minecoin --timeout=300s
    }
}

Invoke-Step "Ожидание Gateway" {
    kubectl wait --timeout=180s -n minecoin gateway/eg --for=condition=Programmed
}

Invoke-Step "Состояние подов" {
    kubectl get pods -n minecoin
}

Write-Host ""
Write-Host "Адрес приложения через Envoy Gateway:" -ForegroundColor Green
Write-Host "http://$(minikube ip):30081"
