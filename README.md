# MineCoin

Учебное клиент-серверное приложение: кошелек для игровой валюты MineCoin (MNC).
Пользователь регистрируется, пополняет и снимает монеты, переводит их другим пользователям по логину и смотрит историю
операций.

Описание домена, сущностей и соответствия 12 факторам — в [Отчёт.md](Отчёт.md).

## Состав

```mermaid
Браузер --> Frontend (nginx Api gateway)

    - "/api/wallets" --> F[finance-service]
        - База данных finance-service --> U[user-service]
        
    - "/api/auth, /api/users" --> U[user-service]
        - База данных user-service
```

| Сервис                            | Что делает                                                 | Адрес (режим разработки)              |
|-----------------------------------|------------------------------------------------------------|---------------------------------------|
| `frontend`                        | nginx: раздаёт HTML/CSS/JS и проксирует `/api/*` в сервисы | http://localhost:8080                 |
| `user-service`                    | регистрация, вход (JWT), профиль, роли                     | http://localhost:8081/swagger-ui.html |
| `finance-service`                 | кошельки, пополнение, снятие, переводы, история            | http://localhost:8082/swagger-ui.html |
| `postgres`                        | PostgreSQL 17, базы `users_db` и `finance_db`              | localhost:5432                        |
| `user-migrate`, `finance-migrate` | разовые процессы миграций Flyway                           | —                                     |
| `user-create-admin`               | разовый процесс создания администратора (профиль `tools`)  | —                                     |

Порты сервисов задаются в `.env` (`FRONTEND_PORT`, `USER_SERVICE_PORT`, `FINANCE_SERVICE_PORT`, `POSTGRES_PORT`).

## Быстрый старт

Нужен Docker с Compose v2.

1. Создать файл окружения из примера:
   ```bash
   cp .env.example .env
   ```
2. Заменить в `.env` значение `JWT_SECRET` на случайную строку не короче 32 байт и при желании пароли `change-me-*`.
   Сгенерировать секрет можно так:
   ```bash
   openssl rand -base64 48
   ```
3. Собрать и запустить (миграции выполнятся до старта сервисов):
   ```bash
   docker compose up --build -d --wait
   ```
4. Создать администратора из `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD`:
   ```bash
   docker compose --profile tools run --rm user-create-admin
   ```
5. Открыть http://localhost:8080 и зарегистрироваться.

Остановить всё:

```bash
docker compose down
```

Остановить и удалить данные базы:

```bash
docker compose down -v
```

## Два режима запуска

| Команда                                   | Файлы Compose                                                      | Наружу открыто                                                  |
|-------------------------------------------|--------------------------------------------------------------------|-----------------------------------------------------------------|
| `docker compose up`                       | `docker-compose.yml` + автоматически `docker-compose.override.yml` | приложение, Swagger обоих сервисов, PostgreSQL — для разработки |
| `docker compose -f docker-compose.yml up` | только `docker-compose.yml`                                        | только `frontend` — для в продакшена                            |

Масштабирование работает в режиме «продакшен»:

```bash
docker compose -f docker-compose.yml up -d --wait --scale user-service=2 --scale finance-service=2
```

nginx используется для нахождения новых экземпляров и распределения запросов между ними.

## Разовые административные процессы

Запускаются из тех же образов и с той же конфигурацией, что и сервисы.

| Задача                                                         | Команда                                                                                                                  |
|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| миграции БД                                                    | выполняются автоматически сервисами `user-migrate` и `finance-migrate`; повторно: `docker compose run --rm user-migrate` |
| создать администратора или повысить существующего пользователя | `docker compose --profile tools run --rm user-create-admin`                                                              |

## Разработка

### Тесты

Нужны JDK 25 и запущенный Docker (интеграционные тесты поднимают PostgreSQL через Testcontainers). Maven ставить не
нужно — используется Maven Wrapper.

```bash
./mvnw verify
```

### Запуск сервисов из IDE

Сервисы читают конфигурацию только из переменных окружения. Для запуска из IntelliJ IDEA нужны два локальных env-файла (
они в `.gitignore`), указанные в конфигурациях запуска. PostgreSQL при этом работает в Docker (
`docker compose up -d postgres user-migrate finance-migrate`).

`.env.user.local` — для `UserServiceApplication`:

```
SERVER_PORT=8009
DB_URL=jdbc:postgresql://localhost:5432/users_db
DB_USERNAME=user_svc
DB_PASSWORD=<USER_DB_PASSWORD из .env>
JWT_SECRET=<JWT_SECRET из .env>
```

`.env.finance.local` — для `FinanceServiceApplication`:

```
SERVER_PORT=8019
DB_URL=jdbc:postgresql://localhost:5432/finance_db
DB_USERNAME=finance_svc
DB_PASSWORD=<FINANCE_DB_PASSWORD из .env>
JWT_SECRET=<JWT_SECRET из .env>
USER_SERVICE_URL=http://localhost:8009
```

### Фронтенд

Обычные HTML, CSS (Pico CSS 2.1.1) и JavaScript-модули в `Frontend/public`, без сборки. После изменений пересобрать
контейнер:

```bash
docker compose up --build -d frontend
```

## Структура репозитория

```
MineCoin/
├── pom.xml, mvnw, .mvn/          родительский POM и Maven Wrapper
├── UserService/                  сервис пользователей (Spring Boot)
├── FinanceService/               сервис финансов (Spring Boot)
├── Frontend/                     nginx: статика и API gateway
├── infra/postgres/init/          создание баз и пользователей PostgreSQL
├── docker-compose.yml            общее описание окружения
├── docker-compose.override.yml   порты для разработки
└── .env.example                  все переменные окружения с примерами
```

Каждый сервис устроен одинаково: `domain` (сущности и правила), `service` (сценарии и репозитории), `infrastructure` (
контроллеры, безопасность, внешние клиенты). Направление зависимостей проверяет тест ArchUnit.

