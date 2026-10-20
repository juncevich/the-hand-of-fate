# План: Keycloak как сервер авторизации

Статус: **этап 1 — в работе** · создан 2026-10-20

Как вести файл:
- Пункт отмечается `[x]` в том же коммите, где сделана работа. Сделано частично — пункт остаётся `[ ]`, а в «Журнал» пишется, что уже готово.
- Этапы идут по порядку. После каждого этапа стек должен работать, а CI быть зелёным (skill `release-checklist`).
- Если решение по ходу меняется, его нужно поправить в разделе «Решения» и записать причину в «Журнал».

Чтобы продолжить работу: прочитать «Решения» и «Журнал» и взять первый неотмеченный пункт.

## Цель

Регистрацией, входом, паролями, подтверждением email и токенами управляет Keycloak (realm `fate`). Вход через соцсети (Google, Яндекс, GitHub) настраивается в Keycloak через Identity Brokering. Бэкенд становится чистым resource server: он проверяет RS256-токены Keycloak по JWKS. Своя аутентификация удаляется: HS256 JWT, refresh-токены, email-токены, throttling. Бот и gRPC не меняются.

Реальных пользователей нет, переносить их не нужно: демо-пользователь заводится в конфигурации realm для dev/test.

## Решения

1. **Тот же origin.** Keycloak работает за gateway по пути `/auth/` (`KC_HTTP_RELATIVE_PATH=/auth`), `KC_HOSTNAME` — публичный адрес. Поэтому SSO-cookie остаётся first-party, CORS не нужен, а `iss` одинаковый в любом окружении. JWKS бэкенд берёт по внутреннему адресу.
2. **SPA — public client `fate-spa`.** Authorization Code + PKCE (S256), библиотека `oidc-client-ts`. Refresh-токены клиенту не выдаём («Use refresh tokens» = off). Access-токен хранится только в памяти и обновляется тихим `prompt=none`. BFF не выбран: он делает бэкенд stateful и требует CSRF и второго механизма аутентификации.
3. **`sub` = `users.id`.** `AuthenticatedUser` и все FK не меняются. Строка в `users` создаётся при первом запросе (JIT) из claims токена.
4. **Проверка токена:** `iss`, `exp`, подпись RS256, `aud=fate-api` (через audience mapper), `email_verified=true`.
5. **Смена email в Keycloak отключена.** По email голосования находят участников.
6. **Повторная аутентификация.** Опасные операции (удаление аккаунта) требуют, чтобы `auth_time` был не старше 5 минут. Иначе ответ `REAUTHENTICATION_REQUIRED`, SPA делает `signinRedirect({ max_age: 0 })`.
7. **Realm как код.** Файлы `infra/keycloak/realm/` применяет keycloak-config-cli (идемпотентно), секреты передаются через env-плейсхолдеры. Клиенты:
   - `fate-spa` — public, PKCE;
   - `fate-backend` — confidential, service account с `manage-users`;
   - `fate-test` — Direct Grant, только dev/e2e;
   - `fate-swagger` — PKCE, только dev.
8. **Соцсети.** First Broker Login без автоматической привязки: если аккаунт с таким email уже есть, привязка подтверждается входом в него или письмом. «Trust email» включаем только у провайдеров, которые сами подтверждают email. Яндекс подключается через generic OIDC/OAuth2 или `keycloak-russian-providers`.
9. **Страницы входа — от Keycloak.** Своих форм поверх ROPC не делаем. Тема — см. открытый вопрос.
10. **Без большого переключения.** На время перехода бэкенд принимает и свой HS256-токен, и токены Keycloak (`JwtIssuerAuthenticationManagerResolver`).

## Открытые вопросы

- [ ] Тема страниц входа. Рекомендация: стандартная тема Keycloak с русской локалью на старте, Keycloakify — отдельным этапом 6. Решить до этапа 6.
- [x] Версия Keycloak и JDK для systemd-деплоя. Решено: Keycloak 26.8.0, работает на JDK 21 (он и в образе). На сервере ставим Temurin 21 рядом с Java 26 и в юните Keycloak указываем `JAVA_HOME` (этап 5).

## Этап 0. Подготовка

- [x] ADR в `docs/adr/` с решениями 1–10 (`docs/adr/0001-keycloak-authorization-server.md`).
- [x] Выбрать и закрепить тег `quay.io/keycloak/keycloak` и keycloak-config-cli: последний стабильный, не моложе двух недель. `quay.io/keycloak/keycloak:26.8.0` (26.8.1 моложе двух недель) и `adorsys/keycloak-config-cli:6.5.1-26`.

## Этап 1. Keycloak во всех окружениях

- [x] `infra/keycloak/realm/`:
  - realm `fate`: локаль `ru` по умолчанию, обязательная верификация email, brute force detection, access-токен живёт 5 минут;
  - клиенты из решения 7;
  - audience mapper `fate-api`.
- [x] Отдельный dev/e2e-оверлей realm: клиент `fate-test` и демо-пользователь `admin@admin.com` / `admin123` (`infra/keycloak/realm-dev/`; `fate-test` и `fate-swagger` в основном файле, включаются через `KC_DEV_CLIENTS_ENABLED`).
- [x] Identity providers Google, GitHub и Яндекс. Включаются только при заданных секретах; в First Broker Login нет автоматической привязки.
- [x] Postgres: init-скрипт для базы и пользователя `keycloak`, для compose и k8s (`infra/keycloak/create-database.sh`).
- [x] `docker-compose.infra.yml` и `docker-compose.yml`:
  - сервис `keycloak` (`start-dev`, SMTP → Mailpit, порт 8180);
  - одноразовый сервис config-cli.
- [x] `frontend/vite.config.ts`: proxy `/auth` → Keycloak.
- [x] `infra/nginx/nginx.conf`: `location /auth/` со своими security headers, без CSP приложения. `/auth/admin/` не проксировать за пределы dev.
- [ ] k8s:
  - `overlays/local`: Deployment и Service `keycloak`, configMapGenerator с realm, Job config-cli;
  - секреты в `write-secrets.sh`;
  - `MINIKUBE_MEMORY` 6g → 8g;
  - ресурс keycloak в `Tiltfile`;
  - `e2e`-оверлей.
- [x] `infra/k8s/check.sh` проходит: один и тот же тег образов в compose и манифестах, kubeconform.
- [x] Наблюдаемость: `KC_METRICS_ENABLED` → prometheus receiver в OTel Collector → Mimir, `KC_TRACING_ENABLED` → Tempo, панели Keycloak в `hand-of-fate-overview.json`.
- [x] `.env.example`: переменные `KC_*` и `OAUTH_{GOOGLE,YANDEX,GITHUB}_CLIENT_ID/SECRET`.
- [x] `dev-start.sh`, `k8s-start.sh` и skill `dev-environment` знают про Keycloak.
- [x] Проверка руками на compose (`docker-compose.infra.yml` + Vite): PKCE-поток через `:3000/auth`, `prompt=none`, токены `fate-test`, Admin API от `fate-backend`.
- [ ] Проверка руками на minikube: вход демо-пользователем через `http://localhost:30080/auth/`, письма Keycloak приходят в Mailpit.

## Этап 2. Бэкенд принимает оба выпускающих сервера

- [ ] `JwtIssuerAuthenticationManagerResolver`: старый HS256-декодер и RS256-декодер Keycloak (`jwk-set-uri` — внутренний адрес, `issuer` — публичный).
- [ ] Валидатор claims для Keycloak (решение 4) и конвертер в `AuthenticatedUser`.
- [ ] `CurrentUserProvisioner`: JIT-upsert в `users` по `sub`, `email` и `name`, через `ON CONFLICT DO NOTHING`.
- [ ] Демо-пользователь: у него в Keycloak `sub` не совпадает с `users.id` строки из V7 (Admin API не даёт задать id), и JIT упадёт на уникальности email. В dev/test выключить `seedDemoUser` (V7 оставить: миграции не меняются) и проверить интеграционные тесты, которые на него опираются (`VoteVersioningIntegrationTest`).
- [ ] Порт `IdentityAdminPort` и адаптер Keycloak (`RestClient` + client credentials `fate-backend`): `deleteUser`, `logoutUser`.
- [ ] Удаление аккаунта:
  - проверка `auth_time`;
  - после commit — удаление пользователя в Keycloak, надёжно: outbox или ретраи.
- [ ] `logout-all` → `IdentityAdminPort.logoutUser`.
- [ ] `ErrorCode.REAUTHENTICATION_REQUIRED`: в Kotlin, proto (skill `proto-sync`), `frontend/src/lib/errorMessages.ts`, `bot/internal/handler/errors.go`.
- [ ] `SecretsValidator`: секрет клиента `fate-backend`.
- [ ] Тесты:
  - тестовый RSA-ключ и `tokenFor(user)` в `AbstractApiIntegrationTest`;
  - юнит-тесты provisioner/validator/adapter;
  - smoke-тест на `testcontainers-keycloak`.
- [ ] `ModularityTest` и JaCoCo (95/85) зелёные.

## Этап 3. Фронтенд на Keycloak

- [ ] Зависимость `oidc-client-ts`, `AuthProvider` и `UserManager` (`fate-spa`, PKCE, `automaticSilentRenew` через `prompt=none`).
- [ ] Маршруты `/oidc/callback` и `/oidc/silent`. Путь `/auth/` занят Keycloak на gateway, поэтому не он.
- [ ] Gateway: `/oidc/silent` грузится в iframe, а у SPA `X-Frame-Options: DENY` и `frame-ancestors 'none'`. Нужен отдельный `location = /oidc/silent` (или статический `silent.html`) с `frame-ancestors 'self'` и `X-Frame-Options: SAMEORIGIN`. То же в Vite и в nginx фронтенд-образа.
- [ ] `authStore` получает токен из `UserManager`.
- [ ] `api/client.ts`: на 401 `signinSilent()` и повтор запроса, иначе `signinRedirect()`. Убрать свой refresh и Web Lock.
- [ ] Обработка `REAUTHENTICATION_REQUIRED`: `signinRedirect({ max_age: 0 })` с возвратом к действию.
- [ ] Settings:
  - «Сменить пароль» → `kc_action=UPDATE_PASSWORD`;
  - «Связанные аккаунты» → account console;
  - «Выйти везде»;
  - удаление аккаунта без поля пароля.
- [ ] Выход: RP-initiated logout (`end_session_endpoint`).
- [ ] Удалить `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `VerifyEmailPage` и их тесты; вместо входа — redirect на Keycloak.
- [ ] `dev:mock`: заглушка `AuthProvider` с демо-пользователем.
- [ ] Тесты Vitest, `npm run lint`, `npm run build`.

## Этап 4. Клиенты API и e2e

- [ ] simulation:
  - пользователи создаются через Admin API (`emailVerified=true`), токены — через `fate-test`;
  - сценарий `session` переписать на проверку токенов.
- [ ] perf: убрать `AuthSimulation`, `VoteSimulation` и `SmokeSimulation` получают токены через `fate-test`.
- [ ] Учесть: в токенах `fate-test` (Direct Grant) нет `auth_time`. Операции с повторной аутентификацией (удаление аккаунта) тестировать через браузерный поток.
- [ ] e2e:
  - `support/accounts.ts` и `apiAs()` через Keycloak;
  - `auth.spec`: регистрация и сброс пароля на страницах Keycloak, письма через Mailpit;
  - `account.spec`: смена пароля через AIA.
- [ ] e2e: вход через соцсеть с `mock-oauth2-server` как OIDC-провайдером Keycloak, только в e2e-оверлее.
- [ ] `infra/k8s/e2e.sh` и `e2e.yml` зелёные.

## Этап 5. Удалить старую аутентификацию

- [ ] Убрать HS256-декодер и resolver двух выпускающих серверов: остаётся только Keycloak.
- [ ] Удалить код:
  - `AuthService`, `JwtTokenProvider`, `JwtConfig` (HS256), `JwtProperties`;
  - `RefreshToken*`, `EmailToken*`, `LoginThrottle`, `RateLimitFilter`, `PasswordConfirmation`;
  - `AuthController`, `AccountController`;
  - регистрацию, верификацию и сброс в `AccountService`.
- [ ] `notification`: убрать payload'ы `EMAIL_VERIFICATION` и `PASSWORD_RESET` с их шаблонами.
- [ ] `ExpiredTokenPurger`: оставить только Telegram-токены.
- [ ] Миграция V25 (skill `new-migration`): `DROP TABLE refresh_tokens, email_tokens`; `DROP COLUMN users.password_hash, users.email_verified_at`. Seed V7 больше ни на что не влияет: проверить, что плейсхолдер `seedDemoUser` тоже убран.
- [ ] Устаревшие `ErrorCode` пометить deprecated, в proto сделать `reserved`, не удалять и не переиспользовать. Контрактные тесты зелёные.
- [ ] Убрать `JWT_ACCESS_SECRET` и `JWT_*`: из конфигурации, `SecretsValidator`, `.env.example`, deploy-workflows.
- [ ] OpenAPI:
  - security scheme `oauth2` authorizationCode, Swagger через `fate-swagger`;
  - `make openapi`;
  - удалённые `/auth/*` записать в `api/accepted-breaking-changes.txt`.
- [ ] Прод: Keycloak как systemd-юнит в `server-setup.yml` (`kc.sh build`, `proxy-headers=xforwarded`, HTTPS, location `/auth/` в nginx), секреты в deploy-workflows.
- [ ] CLAUDE.md: Auth Flow, Key Environment Variables, команды, миграции, Testing, Observability (Keycloak в коллекторе и дашборде), демо-пароль `admin123` (README, `backend/README.md`, `perf/README.md`, `k8s-start.sh`, Tiltfile).
- [ ] Skill `release-checklist` проходит полностью.

## Этап 6. Тема страниц входа (после решения открытого вопроса)

- [ ] Keycloakify-тема в стиле SPA (тёмная/светлая, Tailwind): login, register, reset, verify email.
- [ ] Русские шаблоны писем Keycloak в стиле `EmailService`.
- [ ] Сборка jar темы в CI и подключение в compose, k8s и прод.

## Журнал

- 2026-10-20 — план составлен. Реальных пользователей нет, перенос не нужен.
- 2026-10-20 — этапы 0–1 (кроме проверки на minikube). Находки:
  - keycloak-config-cli 6.5.1 официально собран под 26.5, но с Keycloak 26.8.0 работает: realm применяется, повторный прогон идемпотентен.
  - Яндекс подключён встроенным generic-провайдером `oauth2` (есть в 26.8), расширение `keycloak-russian-providers` не нужно.
  - Демо-пароль в Keycloak — `admin123`: `admin` не проходит политику realm (`length(8)`). Политику ради dev не ослабляли.
  - Метрика `keycloak_user_events_total{event,error,realm}` включается feature `user-event-metrics` + `KC_EVENT_METRICS_USER_ENABLED`. Event listener `metrics-listener` в 26.8 не существует (ошибка «provider not found»), поэтому в realm только `jboss-logging`.
  - `KC_HOSTNAME` — полный публичный URL с `/auth`: `http://localhost:3000/auth` для dev-start (Vite проксирует `/auth` → `:8180`), `http://localhost/auth` для полного compose (gateway :80), `http://localhost:30080/auth` для minikube. JWKS бэкенд берёт по внутреннему адресу (`:8180` / `keycloak:8080`), `iss` всегда публичный.
  - В k8s Job `keycloak-config` неизменяемый, поэтому `k8s-start.sh` удаляет его перед apply, а Tilt пересоздаёт сам.
