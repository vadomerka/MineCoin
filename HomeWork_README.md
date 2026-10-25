## Информация для сдачи ДЗ

- **Номер дз:** 4
- **Ветка проекта:** metrics
- **Расположение отчета по текущему дз:** [k8s/monitoring/Отчёт.md](k8s/monitoring/%D0%9E%D1%82%D1%87%D1%91%D1%82.md)

- **Артефакты для проверки:**

  - Helm values VictoriaMetrics: [k8s/monitoring/vmsingle-values.yaml](k8s/monitoring/vmsingle-values.yaml)
  - Экспорт метрик рантайма: `pom.xml` и `application.yml` сервисов `UserService` и `FinanceService`
  - Бизнес-метрики: [UserService.java](UserService/src/main/java/com/minecoin/user/service/UserService.java), [WalletService.java](FinanceService/src/main/java/com/minecoin/finance/service/WalletService.java)
  - postgres-exporter и аннотации для service discovery: [k8s/10_postgres.yml](k8s/10_postgres.yml), [k8s/22_user_service.yml](k8s/22_user_service.yml), [k8s/32_finance_service.yml](k8s/32_finance_service.yml)
