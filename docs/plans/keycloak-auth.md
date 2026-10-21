# План: Keycloak как сервер авторизации

Статус: **этап 5 — в работе** (этапы 0–4 готовы; осталось: прод и release-checklist) · создан 2026-10-20

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
10. **Без большого переключения.** На время перехода бэкенд принимал и свой HS256-токен, и токены Keycloak (свой `IssuerRoutingJwtDecoder`, этап 2). С этапа 5 — только Keycloak.

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
- [x] k8s:
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
- [x] Проверка руками на minikube: вход демо-пользователем через `http://localhost:30080/auth/` (PKCE через gateway), токен принят бэкендом через `/api`, строка V7 перенята; письмо Keycloak («Сброс пароля») пришло в Mailpit.

## Этап 2. Бэкенд принимает оба выпускающих сервера

- [x] Бэкенд принимает оба вида токенов: старый HS256-декодер и RS256-декодер Keycloak (`jwk-set-uri` — внутренний адрес, `issuer` — публичный). Вместо `JwtIssuerAuthenticationManagerResolver` — свой `IssuerRoutingJwtDecoder`: у старых токенов нет `iss`, а стандартный resolver такие отвергает.
- [x] Валидатор claims для Keycloak (решение 4) и конвертер в `AuthenticatedUser`; `auth_time` попадает в `AuthenticatedUser.authenticatedAt`.
- [x] `KeycloakUserProvisioner`: JIT-upsert в `users` по `sub`, `email` и `name`, через `ON CONFLICT DO NOTHING`. Перенимает старый подтверждённый аккаунт с тем же email (V25: `ON UPDATE CASCADE`), заменяет неподтверждённый, не воскрешает удалённый (`deleted_accounts`).
- [x] Демо-пользователь: его `sub` в Keycloak не совпадает с `users.id` из V7, поэтому строка V7 перенимается при первом входе через Keycloak. `seedDemoUser` выключать не нужно.
- [x] Порт `IdentityAdminPort` и адаптер Keycloak (`RestClient` + client credentials `fate-backend`): `deleteUser`, `logoutUser` (`KeycloakAdminClient`; 404 считается успехом).
- [x] Удаление аккаунта:
  - проверка `auth_time`;
  - после commit — удаление пользователя в Keycloak, надёжно: `deleted_accounts` + `IdentityDeletionWorker` (раз в 30 с, повторы до успеха). В `DeleteAccountRequest` поле `password` стало необязательным: без пароля нужен вход не позже 5 минут назад.
- [x] `logout-all` → `IdentityAdminPort.logoutUser`.
- [x] `ErrorCode.REAUTHENTICATION_REQUIRED`: в Kotlin, proto (skill `proto-sync`), `frontend/src/lib/errorMessages.ts`, `bot/internal/handler/errors.go`.
- [x] `SecretsValidator`: секрет клиента `fate-backend` (`KEYCLOAK_CLIENT_SECRET`).
- [x] Тесты:
  - тестовый RSA-ключ и `keycloakToken(...)` в `AbstractApiIntegrationTest` (`FakeKeycloak`: JWKS, токен service account, Admin API);
  - юнит-тесты provisioner/validator/adapter;
  - smoke-тест на настоящем Keycloak в Testcontainers (`KeycloakSmokeIntegrationTest`, realm применяется keycloak-config-cli из `infra/keycloak/`; отдельная зависимость `testcontainers-keycloak` не нужна).
- [x] `ModularityTest` и JaCoCo (95/85) зелёные (`check`: 333 unit + 144 интеграционных, покрытие 98,6% строк / 87,6% ветвей).

## Этап 3. Фронтенд на Keycloak

- [x] Зависимость `oidc-client-ts` 3.5.0, `UserManager` (`fate-spa`, PKCE, `automaticSilentRenew` через `prompt=none`) — `src/auth/keycloak.ts`; общий интерфейс `src/auth/session.ts`.
- [x] Маршрут `/oidc/callback` (`OidcCallbackPage`) и статическая страница `/oidc/silent.html` (второй вход сборки Vite, `src/oidc-silent.ts`). Путь `/auth/` занят Keycloak на gateway, поэтому не он.
- [x] Gateway: `location = /oidc/silent.html` с `frame-ancestors 'self'` и `X-Frame-Options: SAMEORIGIN`; у остального SPA по-прежнему `DENY`. Vite и nginx фронтенд-образа заголовков фреймов не ставят, им ничего не нужно.
- [x] `authStore` получает токен из `UserManager` (вход, `addUserLoaded` при фоновом продлении); профиль — новый `GET /api/v1/users/me`.
- [x] `api/client.ts`: на 401 одно общее продление (`session.renew` → `signinSilent()`) и повтор запроса, иначе сессия очищается и ProtectedRoute ведёт на `/login`. Свой refresh, cookie и Web Lock убраны.
- [x] Обработка `REAUTHENTICATION_REQUIRED`: `signIn({ reauthenticate: true })` → `prompt=login`, возврат на `/settings?delete-account` с открытым диалогом.
- [x] Settings:
  - «Сменить пароль» → `kc_action=UPDATE_PASSWORD`;
  - «Связанные аккаунты» → account console;
  - «Выйти везде»;
  - удаление аккаунта без поля пароля.
- [x] Выход: RP-initiated logout (`end_session_endpoint`).
- [x] Удалить `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `VerifyEmailPage`, `ResendVerification`, `ChangePasswordCard` и их тесты. `LoginPage` осталась стартовой страницей с кнопками «Войти» и «Создать аккаунт» (`prompt=create`), обе ведут в Keycloak.
- [x] `dev:mock`: `src/auth/mock.ts` + `POST /api/v1/mock/sign-in` в `mock-plugin.ts` (всегда демо-аккаунт); имитация входа по паролю, refresh и писем из mock-API убрана.
- [x] Тесты Vitest (169), `npm run lint`, `tsc -b`, `vite build`.
- [x] Проверка в браузере на minikube (Playwright-скрипт, 10 шагов): вход; перезагрузка без повторного входа (тихое восстановление через iframe); смена пароля через AIA; выход и «выйти везде»; регистрация → подтверждение email из Mailpit → пароль → вход; удаление аккаунта без пароля → пользователь удалён и в Keycloak. Ошибок CSP нет.

## Этап 4. Клиенты API и e2e

- [x] simulation: пользователи регистрируются на страницах Keycloak, как в браузере (`internal/keycloak.Browser`: `fate-spa`, PKCE, письмо из Mailpit, пароль после подтверждения), админские права не нужны. Сценарий `session` переписан: неверный пароль, второй браузер, тихое продление (`prompt=none`), RP-logout, logout-all. Тесты на фейковом Keycloak (`keycloaktest`). Новый флаг `-app` (origin SPA).
- [x] perf: `AuthSimulation` удалён, `SmokeSimulation` и `VoteSimulation` получают токены через `fate-test` (`Keycloak.kt`, `-DkeycloakUrl`). Заодно исправлена давняя ошибка: создание голосования отвечает 201, а проверка ждала 200. `VoteSimulation` на minikube — 630/630.
- [x] Учесть: в токенах `fate-test` (Direct Grant) нет `auth_time`. Удаление аккаунта тестируется через браузерный поток (`account.spec`).
- [x] e2e: `support/accounts.ts` (регистрация на форме Keycloak, пароль после письма) и `apiAs()` через `fate-test`; `auth.spec` (регистрация, восстановление сессии при перезагрузке, сброс пароля), `account.spec` (смена пароля через AIA, «выйти везде», удаление — пользователь исчезает и в Keycloak).
- [x] e2e: вход через соцсеть (`social.spec`) с `mock-oauth2-server` 6.0.4 как OIDC-провайдером `e2e-mock` — только в e2e-оверлее (`overlays/e2e/mock-oidc.yaml`, `/mock-oidc/` в gateway).
- [x] `infra/k8s/e2e.sh` зелёный локально (simulation теперь через gateway, плюс метрики и трассы Keycloak); Playwright 13/13 на e2e-оверлее. CI (`e2e.yml`) проверится на пуше.

## Этап 5. Удалить старую аутентификацию

- [x] Убрать HS256-декодер и resolver двух выпускающих серверов: остаётся только Keycloak.
- [x] Удалить код:
  - `AuthService`, `JwtTokenProvider`, `JwtConfig` (HS256), `JwtProperties`;
  - `RefreshToken*`, `EmailToken*`, `LoginThrottle`, `RateLimitFilter`, `PasswordConfirmation`;
  - `AuthController`, `AccountController`;
  - регистрацию, верификацию и сброс в `AccountService`.
- [x] `notification`: убрать payload'ы `EMAIL_VERIFICATION` и `PASSWORD_RESET` с их шаблонами.
- [x] `ExpiredTokenPurger`: оставить только Telegram-токены.
- [x] Миграция V26 (skill `new-migration`; V25 занята этапом 2): `DROP TABLE refresh_tokens, email_tokens`; `DROP COLUMN users.password_hash, users.email_verified_at`; заодно удаляет неподтверждённые аккаунты и письма `EMAIL_VERIFICATION`/`PASSWORD_RESET` из outbox. Плейсхолдер `seedDemoUser` выключен во всех профилях, но остаётся в `application.yml`: V7 его читает, а без него Flyway падает.
- [x] Устаревшие `ErrorCode` пометить deprecated, не удалять и не переиспользовать. Контрактные тесты зелёные. В proto вместо `reserved` — `[deprecated = true]`, см. журнал.
- [x] Убрать `JWT_ACCESS_SECRET` и `JWT_*`: из конфигурации, `SecretsValidator`, `.env.example`, deploy-workflows.
- [x] OpenAPI:
  - security scheme `oauth2` authorizationCode, Swagger через `fate-swagger`;
  - `make openapi`;
  - удалённые `/auth/*` записать в `api/accepted-breaking-changes.txt`.
- [ ] Прод: Keycloak как systemd-юнит в `server-setup.yml` (`kc.sh build`, `proxy-headers=xforwarded`, HTTPS, location `/auth/` в nginx), секреты в deploy-workflows.
- [x] CLAUDE.md: Auth Flow, Key Environment Variables, команды, миграции, Testing, Observability (Keycloak в коллекторе и дашборде), демо-пароль `admin123` (README, `backend/README.md`, `perf/README.md`, `k8s-start.sh`, Tiltfile).
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
  - Проверка на minikube не прошла: на диске хоста кончилось место (свободно было 1,5 ГБ из 213 ГБ). Созданный кластер удалён (`./k8s-stop.sh --delete`), пункт остаётся открытым до повтора. На первом запуске свежего кластера `k8s-start.sh` упёрся в 180 с ожидания postgres: образы ещё скачивались.
- 2026-10-20 — этап 2. Находки и решения:
  - Неподтверждённый старый аккаунт с тем же email не перенимается, а удаляется и создаётся заново: его пароль мог задать посторонний.
  - Новые переменные бэкенда: `KEYCLOAK_URL` (внутренний адрес), `KEYCLOAK_PUBLIC_URL` (issuer = `<public>/realms/fate`), `KEYCLOAK_CLIENT_SECRET`. Значения по умолчанию подходят для dev-start; compose и k8s задают свои.
  - В тестах `@DynamicPropertySource` подкласса не перекрывает те же ключи базового класса, поэтому smoke-тест подставляет адрес настоящего Keycloak через `ApplicationContextInitializer` (`addFirst`).
  - `ErrorMappingContractTest` сравнивает и порядок значений: новый код добавляется в конец Kotlin enum.
- 2026-10-20 — этап 3. Находки и решения:
  - Бэкенду добавлен `GET /api/v1/users/me` (`ProfileResponse`): профиль раньше приходил в ответе на логин.
  - Silent renew — отдельная HTML-страница, а не маршрут SPA: в iframe не грузится всё приложение, путь `/oidc/silent.html` попадает под зарегистрированный `KC_APP_URL/oidc/*`.
  - Повторная аутентификация через `prompt=login`, а не `max_age=0`.
  - `client.ts` сам кладёт продлённый токен в store перед повтором, иначе request-interceptor подставил бы старый.
  - `npm run check:api` до коммита показывает diff — это ожидаемо: он сравнивает с HEAD.
  - e2e (`auth.spec`, `account.spec`) и simulation сломаются до этапа 4: они используют старый вход.
  - `vite build` предупреждает, что основной чанк больше 500 КБ (528 КБ вместе с `oidc-client-ts`); не исправлял.
  - Найдено проверкой в браузере и исправлено:
    - `k8s-start.sh`: `minikube image load --overwrite` не заменял образ, если тег `dev` уже был на узле, — пересобранные backend/frontend молча не доезжали. Теперь `docker save | minikube ssh -- docker load`. Таймаут ожидания postgres 180 → 300 с: на свежем кластере образы качаются дольше.
    - Gateway проксировал Keycloak с `Host $host` (без порта) и `X-Forwarded-Port $server_port` (80 внутри pod): Keycloak отдавал браузеру `token_endpoint` без `:30080`, его блокировал CSP. Теперь `Host`/`X-Forwarded-Host` = `$http_host`, без `X-Forwarded-Port`.
    - `fullScopeAllowed` у клиентов устарел (предупреждения в логе Keycloak): всем клиентам `false`, роли Admin API у `fate-backend` выданы через `clientScopeMappings`.
  - Не ошибка: с `verifyEmail` Keycloak 26.8 не просит пароль на форме регистрации — пароль задаётся после перехода по ссылке из письма (required action `UPDATE_PASSWORD`; так чужой адрес нельзя занять своим паролем). Ошибка JS `addEventListener` на этой странице — дефект стандартной темы Keycloak (ищет поле пароля); уйдёт с темой этапа 6.
  - e2e/simulation ещё на старом входе (этап 4); в журнале этапа 4 учесть порядок регистрации: пароль после подтверждения email.
- 2026-10-21 — этап 4. Находки и решения:
  - Лимит gateway на Keycloak сужен до POST на `login-actions/` (формы входа, регистрации, сброса). Раньше считались все POST, включая token endpoint: «тихие» продления всех пользователей за одним NAT упёрлись бы в 30 в минуту (на `VoteSimulation` 21 из 70 входов получили 503). Brute force по аккаунту закрывает Keycloak, а token endpoint пароли не проверяет (direct grant только в dev/e2e).
  - Tempo упал по OOM (512Mi) при сборке блока с трассами Keycloak (семплирование 1.0) — лимит поднят до 1Gi.
  - Тестовый провайдер `e2e-mock` описан в основном `fate.yaml` (выключен, пока не задан `KC_E2E_IDP_INTERNAL_URL`): список `identityProviders` во втором файле realm заставил keycloak-config-cli удалить Google/GitHub/Яндекс — проверено.
  - Keycloak допускает http для IdP только на «локальный» адрес; `mock-oidc` в кластере резолвится в приватный ClusterIP — подходит, в compose такого хоста нет.
  - У e2e-оверлея не было `namespace:` — ресурсы самого оверлея уходили в `default`. Исправлено.
  - Имя пользователя в приложении теперь «Имя Фамилия» из Keycloak (форма регистрации требует оба поля).
- 2026-10-21 — этап 5, всё кроме прода и release-checklist. Находки и решения:
  - В proto устаревшие `ErrorCode` помечены `[deprecated = true]`, а не `reserved`: `buf breaking` в CI использует правила по умолчанию (FILE), и удаление значения enum ломает его даже с `reserved`. Бот не держит тексты для устаревших кодов: staticcheck (SA1019) ругается на их Go-константы. `TestEveryErrorCodeHasAText` пропускает значения с опцией `deprecated`. В Kotlin коды помечены `@Deprecated` и остались в enum, поэтому фронтенд показывает их тексты по-прежнему (они есть в union из OpenAPI).
  - `KeycloakUserProvisioner` перенимает любую строку с тем же email, без проверки подтверждения: неподтверждённые аккаунты удалила V26, а новых без Keycloak не появится.
  - `delete-account` больше не принимает тело (пароля нет), `logout-all` и `delete-account` остались по прежним путям `/api/v1/auth/*` — SPA менять не пришлось, кроме пустого тела запроса. `RateLimitFilter`/`LoginThrottle` удалены, лимит gateway на `/api/v1/auth/` тоже; per-IP лимит остался только на формах Keycloak.
  - OpenAPI: к `bearerAuth` добавлена альтернатива `keycloak` (oauth2 authorization code). Адреса — из `app.keycloak.public-url`, поэтому `FakeKeycloak.PUBLIC_URL` теперь dev-значение `http://localhost:3000/auth`: оно попадает в закоммиченный `api/openapi.yaml`. В dev-профиле Swagger UI настроен на клиент `fate-swagger` с PKCE. Удалённые эндпоинты записаны в `api/accepted-breaking-changes.txt` (старую запись про `register` 201 заменил: эндпоинта больше нет).
  - Проверено: backend `check` (259 unit + 105 интеграционных, покрытие 98,4% строк / 86,6% ветвей), bot (`go test`, staticcheck, `buf lint`/`breaking` против main), simulation, frontend (lint, 169 тестов, `tsc -b`), `infra/k8s/check.sh`, `oasdiff breaking` с исключениями. На minikube (`K8S_OVERLAY=e2e ./k8s-start.sh` поверх вчерашней базы): V26 применилась к данным V25 без ошибок, `infra/k8s/e2e.sh` зелёный (simulation 4/4, письма, метрики/логи/трассы бэкенда и Keycloak), Playwright 13/13, в логах бэкенда ошибок нет. Также прошли `vite build`, typecheck e2e, perf (spotless, detekt, компиляция), staticcheck simulation. `npm run check:api` до коммита показывает diff — ожидаемо, он сравнивает с индексом git.
  - Осталось: `server-setup.yml` всё ещё ставит nginx с `limit_req` на `/api/v1/auth/` и без Keycloak — это пункт «Прод». В `backend/openapi.yaml` лежит старая, никем не используемая копия спецификации (с `/auth/register`) — не трогал.
