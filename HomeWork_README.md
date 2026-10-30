## Информация для сдачи ДЗ

- **Номер дз:** 5
- **Ветка проекта:** feature/SLI
- **Расположение отчета по текущему дз:** [k8s/slo/Отчёт.md](k8s/slo/%D0%9E%D1%82%D1%87%D1%91%D1%82.md)

- **Артефакты для проверки:**

  - Пробер: модуль [Prober](Prober), манифест [k8s/50_prober.yml](k8s/50_prober.yml)
  - Корзины 300 мс для SLI задержки: `application.yml` сервисов `UserService` и `FinanceService`
  - Инъекция сбоев для демонстрации: [FinanceService/.../infrastructure/fault](FinanceService/src/main/java/com/minecoin/finance/infrastructure/fault), [k8s/30_finance_service_config.yml](k8s/30_finance_service_config.yml)
  - Срок хранения метрик 31 день: [k8s/monitoring/vmsingle-values.yaml](k8s/monitoring/vmsingle-values.yaml)
  - Развёртывание: [k8s/deploy.ps1](k8s/deploy.ps1)
