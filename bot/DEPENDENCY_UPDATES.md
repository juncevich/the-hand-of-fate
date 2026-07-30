# Dependency Updates

## 2026-07-30

Re-checked against the Go module proxy via `go list -m -versions <module>` for each direct dependency, per explicit user request to include majors. No new major branches exist for any direct dependency (`telegram-bot-api/v5`, `spf13/viper`, `go.uber.org/zap`, `google.golang.org/grpc`, `google.golang.org/protobuf` all remain the newest major line). Ran `go get -u ./...` + `go mod tidy`.

### google.golang.org/grpc `v1.82.0 → v1.83.0`
- Routine minor bump — a stable `v1.83.0` was published since the last audit (`v1.82.1` was the newest at that time)

### go.yaml.in/yaml/v3 `v3.0.4 → v3.0.5` (indirect, via spf13/viper)
- Routine patch bump

### google.golang.org/genproto/googleapis/rpc (indirect, pseudo-version)
- `v0.0.0-20260706201446-... → v0.0.0-20260729162451-...` — sync of generated `.pb.go` with latest googleapis proto definitions

Direct dependencies otherwise unchanged (`telegram-bot-api/v5` `v5.5.1`, `spf13/viper` `v1.21.0`, `go.uber.org/zap` `v1.28.0`, `google.golang.org/protobuf` `v1.36.11`). `go build ./...`, `go vet ./...`, and `go test ./...` all pass with no code changes.

## 2026-07-10

Re-checked against the Go module proxy via `go list -u -m all`, per explicit user request to include majors this time. No new major branches exist for any direct dependency (`telegram-bot-api/v5`, `spf13/viper`, `go.uber.org/zap`, `google.golang.org/grpc`, `google.golang.org/protobuf` all remain the newest major line). Ran `go get -u` on all five direct deps + `go mod tidy`.

### golang.org/x/net `v0.56.0 → v0.57.0` (indirect)
- Routine sync of internal `golang.org/x` dependencies

### golang.org/x/sys `v0.46.0 → v0.47.0` (indirect)
- Routine sync

### golang.org/x/text `v0.39.0 → v0.40.0` (indirect)
- Routine sync

Direct dependencies (`telegram-bot-api/v5` `v5.5.1`, `spf13/viper` `v1.21.0`, `go.uber.org/zap` `v1.28.0`, `google.golang.org/grpc` `v1.82.0`, `google.golang.org/protobuf` `v1.36.11`) were already at their latest stable versions and unchanged. `go build ./...` and `go test ./...` pass with no code changes.

## 2026-07-07

Проверено против реального Go module proxy (`go list -m -u all`, `go list -m -versions <module>`) для всех прямых зависимостей (`telegram-bot-api/v5`, `spf13/viper`, `go.uber.org/zap`, `google.golang.org/grpc`, `google.golang.org/protobuf`). Явно проверено наличие новых major-веток по отдельным module path (`telegram-bot-api/v6`, `zap/v2`, `viper/v2`, `grpc/v2`, `protobuf/v2`) — ни один из этих путей не существует в module proxy, так что мажорных апгрейдов среди прямых зависимостей нет; все они уже на последней стабильной версии (`grpc` v1.82.0 — новее есть только `v1.83.0-dev`, pre-release, пропущен намеренно). `go get -u ./...` + `go mod tidy` подтянули обновления только по трём косвенным зависимостям.

### github.com/pelletier/go-toml/v2 `v2.4.2 → v2.4.3` (indirect, via spf13/viper)
- **Безопасность:** ограничена глубина вложенности массивов и inline-таблиц — предотвращает stack-overflow DoS при парсинге вредоносного TOML
- Исправлен паник при nil unexported embedded pointer — теперь возвращается ошибка вместо паники
- Исправлена некорректная генерация TOML для закомментированных multi-line значений
- Исправлена бесконечная рекурсия при рекурсивно вложенных embedded-структурах
- Гарантировано, что корневой `Unmarshaler` получает весь документ целиком
- Добавлена поддержка `RawMessage` marshal (unstable API)
- Улучшены сообщения об ошибках для некорректного размещения таблиц (позиция, ключ)
- Оптимизировано декодирование дат/времени (устранено boxing в интерфейсы)
- Источник: релиз-ноуты `github.com/pelletier/go-toml` на GitHub (tag v2.4.3)

### golang.org/x/text `v0.38.0 → v0.39.0` (indirect)
- Рутинное обновление — синхронизация внутренних golang.org/x зависимостей до последних тегированных версий. Изменений API/поведения в релиз-ноутах не указано.

### google.golang.org/genproto/googleapis/rpc (indirect, псевдо-версия)
- `v0.0.0-20260630182238-925bb5da69e7 → v0.0.0-20260706201446-f0a921348800`. Синхронизация сгенерированных `.pb.go` с последними proto-определениями googleapis; технических деталей содержимого коммита не проверял (как и в предыдущих записях, GitHub commit history для этого репозитория не даёт человекочитаемого changelog per pseudo-version).

### Без изменений (уже на последней доступной версии, включая проверку major-веток)
`github.com/go-telegram-bot-api/telegram-bot-api/v5` (v5.5.1, нет `/v6`), `github.com/spf13/viper` (v1.21.0, нет `/v2`), `go.uber.org/zap` (v1.28.0, нет `/v2`), `google.golang.org/grpc` (v1.82.0, нет `/v2`, только `v1.83.0-dev` pre-release), `google.golang.org/protobuf` (v1.36.11, нет `/v2`), `github.com/fsnotify/fsnotify`, `github.com/go-viper/mapstructure/v2`, `github.com/sagikazarmark/locafero`, `github.com/spf13/afero`, `github.com/spf13/cast`, `github.com/spf13/pflag`, `github.com/subosito/gotenv`, `go.uber.org/multierr`, `go.yaml.in/yaml/v3`, `golang.org/x/net`, `golang.org/x/sys`, `gopkg.in/check.v1`.

Ряд транзитивных модулей, попадающих в `go list -m -u all` (`cel.dev/expr`, `opentelemetry-operations-go/detectors/gcp`, `rogpeppe/go-internal`, `spiffe/go-spiffe/v2`, `go.opentelemetry.io/*`, `golang.org/x/mod`, `golang.org/x/tools`, `google.golang.org/genproto/googleapis/api`, `gopkg.in/check.v1` newer pre-release), не появляются в фактическом графе зависимостей `go.mod` этого модуля (тестовые/build-only транзитивные зависимости чужих go.sum, не используемые в `bot`) — `go mod tidy` не подтянул их, они не входят в реальный dependency graph бинарника.

### Проверка сборки
`go build ./...` — OK. `go vet ./...` — OK. `go test ./...` — все пакеты с тестами (`internal/config`, `internal/handler`) проходят. Изменений в коде для совместимости не потребовалось.

## 2026-07-03

Проверено против реального Go module proxy (`proxy.golang.org/<module>/@latest` и `@v/list`) для каждой зависимости в `go.mod` (прямой и косвенной). Мажорных апгрейдов не потребовалось — ни для одного модуля нет новой major-версии, совместимой с текущим кодом (`telegram-bot-api/v5` остаётся последней веткой, `go.uber.org/zap`, `github.com/spf13/viper` не имеют `/v2`).

### go directive `1.25.0 → 1.26.4`
- Обновлено до последнего стабильного релиза Go (проверено через `https://go.dev/dl/?mode=json`). Рутинное обновление тулчейна, без изменений API.

### google.golang.org/grpc `v1.81.1 → v1.82.0`
- Рутинное обновление минорной версии. Изменений в используемом коде (`bot/internal/grpcclient/`) не потребовалось — `go build ./...` и `go test ./...` проходят без модификаций.

### github.com/pelletier/go-toml/v2 `v2.3.1 → v2.4.2` (indirect, via spf13/viper)
- Рутинное обновление минорной версии.

### google.golang.org/genproto/googleapis/rpc (indirect)
- Псевдо-версия обновлена: `v0.0.0-20260610212136-7ab31c22f7ad → v0.0.0-20260630182238-925bb5da69e7`. Синхронизация сгенерированных `.pb.go` с последними proto-определениями googleapis; технических деталей содержимого коммита не проверял.

### Без изменений (уже на последней доступной версии)
`github.com/go-telegram-bot-api/telegram-bot-api/v5` (v5.5.1), `github.com/spf13/viper` (v1.21.0), `go.uber.org/zap` (v1.28.0), `google.golang.org/protobuf` (v1.36.11), `github.com/fsnotify/fsnotify` (v1.10.1), `github.com/go-viper/mapstructure/v2` (v2.5.0), `github.com/sagikazarmark/locafero` (v0.12.0), `github.com/spf13/afero` (v1.15.0), `github.com/spf13/cast` (v1.10.0), `github.com/spf13/pflag` (v1.0.10), `github.com/subosito/gotenv` (v1.6.0), `go.uber.org/multierr` (v1.11.0), `go.yaml.in/yaml/v3` (v3.0.4), `golang.org/x/net` (v0.56.0), `golang.org/x/sys` (v0.46.0), `golang.org/x/text` (v0.38.0).

### Проверка сборки
`go build ./...` — OK. `go test ./...` — все пакеты с тестами (`internal/config`, `internal/handler`) проходят. Изменений в коде для совместимости не потребовалось.

**Примечание:** CI (`bot.yml`) пинит `go-version: "1.25"` через `actions/setup-go`. После бампа `go` directive до `1.26.4` раннер должен автоматически подтянуть нужный тулчейн через `GOTOOLCHAIN=auto` (сеть в GitHub Actions доступна), но при желании стоит явно обновить пин в workflow до `"1.26"`, чтобы не полагаться на автозагрузку тулчейна в CI.

## 2026-06-11

### golang.org/x/net `v0.55.0 → v0.56.0`
- Дополнительные исправления безопасности в HTML-парсере; обновления QUIC-транспорта

### golang.org/x/sys `v0.45.0 → v0.46.0`
- Обновлены константы и syscall-определения; улучшена поддержка новых платформ

### golang.org/x/text `v0.37.0 → v0.38.0`
- Обновления Unicode-таблиц и локализации

### google.golang.org/genproto/googleapis/rpc (псевдо-версия обновлена)
- Синхронизация с последними proto-определениями googleapis

### go.uber.org/multierr `v1.10.0 → v1.11.0` (indirect)
- Исправление обработки пустых error-слайсов

## 2026-05-25

### google.golang.org/grpc `v1.81.0 → v1.81.1`
- **Безопасность (xDS/RBAC):** Исправлен обход авторизации, при котором URI/DNS SANs некорректно переходили к проверке Subject Distinguished Name. Теперь используется только первый непустой источник идентификации (соответствует gRFC A41)
- **OpenTelemetry:** Исправлена интерференция между клиентским и серверным RPC-контекстом в OTel-плагине — метрики и трейсы с одной стороны перезаписывали данные другой
- Патч-релиз; изменений API нет

### golang.org/x/net `v0.54.0 → v0.55.0`
- **Безопасность — HTML-парсер (несколько CVE):** Исправлена обработка дублирующихся атрибутов (CVE-2026-27136), проверка namespace в end-тегах «in body» (CVE-2026-42506), обработка fostered elements в foreign content (CVE-2026-42502), производительность Noah's Ark clause (CVE-2026-25680), экранирование `>` в DOCTYPE (CVE-2026-25681)
- **QUIC:** Исправлена логика нарезки буферов при перекрывающихся (повторно переданных) данных потока; исправлен `appendMaxDataFrame` (инкрементировал `sentLimit` вместо присвоения); добавлена happens-before синхронизация для предотвращения гонок данных
- **HTTP/2:** Восстановлены символы и методы, случайно утерянные при сборке под Go 1.27 (`WriteScheduler` и связанные константы)

### golang.org/x/sys `v0.44.0 → v0.45.0`
- **OpenBSD:** Добавлены `Readv`, `Writev`, `Preadv`, `Pwritev` через libc; реализация разделяется с Darwin
- **Windows:** Добавлены NT-сисколлы `NtSetEaFile`, `NtQueryEaFile`, `NtQueryInformationFile` — аналог Unix `Fsetxattr`/`Fgetxattr`
- **CPU feature detection:** Добавлено определение `LLACQ_SCREL`, `SCQ`, `DBAR_HINTS` для LoongArch64; определение расширения Zbc для RISC-V 64-bit
- **Linux kernel 7.0:** Обновлены константы и определения syscall до Linux 7.0 API
- Консолидирована реализация `readv`/`writev` между Linux, Darwin и OpenBSD

### google.golang.org/genproto/googleapis/rpc (псевдо-версия от 2026-05-11 → 2026-05-23)
- Автоматически перегенерированные `.pb.go`-файлы из последних proto-определений googleapis
- Включают: поддержку AutoQual cross-branch testing, синхронизацию Google API Expr v1alpha1, flexible CA для Memorystore Redis Cluster, обновления документационных комментариев
- Только технические обновления; новых Go API-поверхностей не добавлено
