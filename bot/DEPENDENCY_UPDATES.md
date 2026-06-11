# Dependency Updates

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
