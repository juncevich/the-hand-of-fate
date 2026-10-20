# ADR 0001: Keycloak как сервер авторизации

- Статус: принято
- Дата: 2026-10-20
- План реализации: [docs/plans/keycloak-auth.md](../plans/keycloak-auth.md)

## Контекст

Аутентификация сейчас целиком своя: регистрация с подтверждением email, вход по паролю, HS256 access JWT на 15 минут, refresh-токены в httpOnly cookie с ротацией и семьями, сброс и смена пароля, throttling по IP и по аккаунту. Нужен вход через внешних провайдеров (OAuth/OIDC). Поддерживать протоколы, привязку аккаунтов, защиту от перебора, MFA и почтовые сценарии самим — это много кода, и всё это критично для безопасности.

Реальных пользователей нет: продакшн-деплой отключён. Переносить учётные записи не нужно.

## Решение

Сервером авторизации становится Keycloak (realm `fate`): он хранит учётные записи и пароли и выдаёт токены. Бэкенд становится чистым OAuth2 resource server.

1. **Тот же origin.** Keycloak работает за gateway по пути `/auth/` (`http-relative-path=/auth`), а `hostname` — публичный URL с `/auth`. Поэтому SSO-cookie Keycloak остаётся first-party, тихое обновление не ломается от блокировки сторонних cookie, CORS не нужен, а `iss` одинаковый в любом окружении. JWKS и Admin API бэкенд вызывает по внутреннему адресу (`hostname-backchannel-dynamic=true`).
2. **SPA — public client `fate-spa`.** Authorization Code + PKCE (S256), библиотека `oidc-client-ts`. Refresh-токены клиенту не выдаются (`use.refresh.tokens=false`). Access-токен хранится только в памяти и обновляется через `prompt=none` в iframe, пока жива httpOnly SSO-cookie. По защите это тот же уровень, что сейчас, и токены, позволяющие продлить сессию, в JS не попадают.
3. **`sub` = `users.id`.** Строка в `users` создаётся при первом запросе (JIT) из claims токена. `AuthenticatedUser` и внешние ключи не меняются.
4. **Проверка токена:** `iss`, `exp`/`nbf`, подпись RS256 по JWKS, `aud` содержит `fate-api` (client scope `fate-api` с audience mapper), `email_verified=true`.
5. **Email нельзя изменить.** `registrationEmailAsUsername=true` и `editUsernameAllowed=false`. Email — ключ участника голосования.
6. **Повторная аутентификация** для опасных операций: `auth_time` не старше 5 минут. Иначе ответ `REAUTHENTICATION_REQUIRED`, SPA делает `signinRedirect({ max_age: 0 })`.
7. **Realm как код.** `infra/keycloak/realm/*.yaml` применяет keycloak-config-cli (идемпотентно, с подстановкой `$(env:…)`). Клиенты:
   - `fate-spa` — public, PKCE;
   - `fate-backend` — confidential, service account с `manage-users` и `view-users`;
   - `fate-test` — public, Direct Grant;
   - `fate-swagger` — public, PKCE.

   `fate-test` и `fate-swagger` включаются только в dev/e2e (`KC_DEV_CLIENTS_ENABLED`). Демо-пользователь лежит в отдельном dev-файле.
8. **Соцсети** подключаются через Identity Brokering:
   - `google` — встроенный провайдер, `trustEmail=true`;
   - `github` — встроенный провайдер, `trustEmail=false`;
   - `yandex` — встроенный generic-провайдер `oauth2` из Keycloak 26.8, `trustEmail=false`.

   Провайдер включается, только если задан его client id. Используется стандартный First Broker Login: если аккаунт с таким email уже есть, привязка подтверждается письмом или входом в этот аккаунт. Автоматической привязки нет.
9. **Страницы входа, регистрации и сброса пароля — от Keycloak.** Свои формы поверх ROPC не делаем. На старте стандартная тема с локалью `ru`, тема Keycloakify — отдельным этапом.
10. **Переход без большого переключения.** На время миграции бэкенд принимает и свой HS256-токен, и RS256-токен Keycloak (`JwtIssuerAuthenticationManagerResolver`). После переключения SPA и клиентов своя аутентификация удаляется целиком.

Версии: Keycloak `26.8.0` (`quay.io/keycloak/keycloak`, внутри JDK 21), keycloak-config-cli `6.5.1-26`.

## Отвергнутые варианты

- **Свой OAuth-клиент на Spring (`oauth2Login`), без внешнего сервера.** Меньше инфраструктуры, но привязка аккаунтов, пароли, письма и защита от перебора остаются нашим кодом.
- **BFF (сессия на бэкенде, токены на сервере).** Самый строгий вариант для браузера. Но бэкенд становится stateful, нужен CSRF и второй механизм аутентификации для simulation, perf и e2e, которые ходят с bearer-токенами.
- **SPA с refresh-токеном в JS.** Refresh-токен становится доступен при XSS: по защите это хуже, чем сейчас.
- **`kc.sh --import-realm` вместо keycloak-config-cli.** Импорт срабатывает только для ещё не созданного realm. Чтобы применить изменения конфигурации, realm пришлось бы пересоздавать, а с ним теряются пользователи.

## Последствия

- Ещё один stateful-сервис: JVM, около 0,5–1 ГБ RAM, отдельная база `keycloak` в том же Postgres. Minikube требует больше памяти.
- В проде Keycloak запускается отдельным systemd-сервисом на своём JDK 21. Приложение остаётся на Java 26.
- «Выйти везде» не отзывает уже выданные access-токены: они живут до `exp` (5 минут).
- Если удалить пользователя в админке Keycloak, локальные данные останутся. Это закрывается периодической сверкой (позже).
- Брутфорс-защиту по аккаунту делает Keycloak (brute force detection), лимит по IP — nginx на `/auth/`.
