# Dependency Updates

## 2026-05-25

### react-router-dom `7.15.0 → 7.15.1`
- Добавлен хук `unstable_useRouterState` — объединяет доступ к активному и ожидающему состоянию роутера (заменяет отдельные вызовы `useLocation`, `useParams`, `useMatches`, `useNavigation`)
- Исправлен `useFetchers` — возвращал нестабильную ссылку, вызывая лишние перерендеры
- Исправлен баг с устаревшими данными в `serverLoader()` при прерывании навигации во время гидратации
- Исправлен `RouterProvider.onError` — не срабатывал для синхронных ошибок первичных загрузчиков в SPA-режиме
- Исправлен конфликт `basename` с именем директории `app` при заданном Vite `base`

### @tanstack/react-query `5.100.10 → 5.100.14`
- Исправлено: React Query больше не переходит в оптимистичное состояние загрузки при отсутствии активных подписчиков — предотвращает лишние сетевые запросы
- Обновлена зависимость `@sveltejs/kit` до 2.57.1 (CVE-2026-40073, затрагивает монорепо, не сам React Query)

### @tanstack/react-query-devtools `5.100.10 → 5.100.14`
- Синхронизирован с core-пакетом; специфичных изменений для devtools нет

### @radix-ui/react-avatar `1.1.3 → 1.1.11`
- Исправлен сломанный импорт `useSyncExternalStore` (v1.1.6) — runtime-breaking баг
- Добавлена зависимость `@radix-ui/react-use-is-hydrated` для улучшенной SSR-гидратации (v1.1.7)
- Заменён устаревший `ElementRef` на `ComponentRef` в типах (v1.1.10)
- Остальные версии: обновления транзитивных зависимостей

### @radix-ui/react-dialog `1.1.6 → 1.1.15`
- Улучшен `useControllableState`: повышена производительность, добавлены предупреждения в консоль при неправильном использовании (v1.1.8)
- Заменён устаревший `ElementRef` на `ComponentRef` (v1.1.14)
- Остальные версии: обновления транзитивных зависимостей; breaking changes отсутствуют

### @radix-ui/react-dropdown-menu `2.1.6 → 2.1.16`
- Улучшен `useControllableState`: производительность и предупреждения при неправильном использовании (v2.1.8)
- Заменён устаревший `ElementRef` на `ComponentRef` (v2.1.15)
- Остальные версии: обновления транзитивных зависимостей; breaking changes отсутствуют

### @radix-ui/react-label `2.1.2 → 2.1.8`
- Заменён устаревший `ElementRef` на `ComponentRef` (v2.1.7)
- Остальные версии: инкрементальные обновления `@radix-ui/react-primitive`; функциональных изменений нет

### @radix-ui/react-select `2.1.6 → 2.2.6`
- **v2.2.0:** Все внутренние bubble inputs теперь используют компонент `Primitive` — даёт потребителям больше контроля над внутренним поведением
- Улучшен `useControllableState`: производительность и предупреждения при неправильном использовании (v2.2.0)
- Заменён устаревший `ElementRef` на `ComponentRef` (v2.2.5)
- Breaking changes для стандартного использования отсутствуют

### @radix-ui/react-separator `1.1.2 → 1.1.8`
- Заменён устаревший `ElementRef` на `ComponentRef` (v1.1.7)
- Остальные версии: инкрементальные обновления `@radix-ui/react-primitive`; функциональных изменений нет

### @radix-ui/react-tabs `1.1.3 → 1.1.13`
- Улучшен `useControllableState`: производительность и предупреждения при неправильном использовании (v1.1.5)
- Заменён устаревший `ElementRef` на `ComponentRef` (v1.1.12)
- Остальные версии: обновления транзитивных зависимостей; breaking changes отсутствуют

### @radix-ui/react-toast `1.2.6 → 1.2.15`
- Улучшен `useControllableState`: производительность и предупреждения при неправильном использовании (v1.2.8)
- **Исправления доступности (v1.2.15):** убран `aria-hidden` с фокусируемых пустых элементов; убран `role=status` у элементов списка (нарушал спецификацию W3C); убран лишний `aria-atomic` по умолчанию у `role=status`
- Заменён устаревший `ElementRef` на `ComponentRef` (v1.2.14)

### @radix-ui/react-tooltip `1.2.4 → 1.2.8`
- Заменён устаревший `ElementRef` на `ComponentRef` (v1.2.7)
- Остальные версии: обновления транзитивных зависимостей; функциональных изменений нет

### lucide-react `1.14.0 → 1.16.0`
- **v1.15.0:** Добавлена иконка `broccoli` и варианты sticky-note; обновлены иконки `text-cursor`, `landmark`, `candy-cane`, `volleyball`, `chart-no-axes-combined`; исправлено клонирование слотов в Vue-компоненте
- **v1.16.0:** Добавлена иконка `blender`

### date-fns `4.1.0 → 4.3.0`
- **v4.2.0:** Только обновление документации — добавлены ссылки на «You Don't Need date-fns» и Temporal API
- **v4.2.1:** Исправлены отсутствующие определения типов из-за ошибки конфигурации TypeScript-сборки в v4.2.0
- **v4.3.0:** Исправлена оптимизация модульных импортов для Next.js и аналогичных инструментов; исправлен первый день недели в португальской локали (воскресенье); исправлен разбор месяцев октябрь–декабрь в китайских локалях (zh-CN, zh-HK, zh-TW)

### vite `8.0.12 → 8.0.14`
- **v8.0.13:** Добавлена поддержка lazy bundling в режиме bundled dev; обновлён rolldown до 1.0.1; исправлены утечки sass/less/styl-воркеров; исправлено копирование public dir при `write=false`; исправлена коллизия label-rewriting при SSR
- **v8.0.14:** Обновлён rolldown до 1.0.2; исправлена обработка ошибок сообщений к Vite-серверу; исправлены пути с trailing slash в `transformIndexHtml`; исправлены OXC JSX options при сканировании зависимостей

### @vitejs/plugin-react `6.0.1 → 6.0.2`
- `reactCompilerPreset` теперь экспортирует все доступные опции (ранее типизировались только `compilationMode` и `target`); изменений поведения нет

### vitest `4.1.6 → 4.1.7`
- Исправлено: ограничена concurrency по ветке задач, а не только по листовым колбэкам — предотвращает конкуренцию за ресурсы

### @testing-library/jest-dom `6.6.3 → 6.9.1`
- **v6.6.4:** Chalk заменён на Picocolors — меньше транзитивных зависимостей
- **v6.7.0:** Новый матчер `toBePressed()` для проверки нажатого состояния кнопки
- **v6.8.0:** Новый матчер `toBePartiallyPressed()` для промежуточного состояния кнопки
- **v6.9.0:** Новые матчеры `toAppearBefore()` / `toAppearAfter()` для проверки порядка DOM-элементов
- **v6.9.1:** Исправлена ошибка `undefined Node error` в среде Node.js

### eslint `10.3.0 → 10.4.0`
- Правило `for-direction` теперь проверяет sequence expressions в условиях цикла
- Добавлен метод `includeIgnoreFile()` в API модуля `eslint/config`
- Исправлено экранирование меток DOT в debug-выводе code path
- Исправлена обработка non-array deprecated rule replacements

### @typescript-eslint/eslint-plugin `8.59.3 → 8.60.0`
- `RuleTester` синхронизирован с улучшениями в upstream ESLint RuleTester; специфичных изменений правил нет

### @typescript-eslint/parser `8.59.3 → 8.60.0`
- Синхронизирован с плагином; специфичных изменений парсера нет

### @types/react `19.1.0 → 19.2.15`
- Добавлены типы для React 19.2: `optimisticKey` для form actions, поддержка `cacheSignal`
- Новые обработчики жестов: `onGestureEnter`, `onGestureExit`, `onGestureShare`, `onGestureUpdate`
- Добавлен атрибут `closedby` для `DialogHTMLAttributes`; добавлен тип popover `hint`; добавлены атрибуты SVG `nonce`, `part`, `slot`
- Совместимость с TypeScript 6.0; убран `digest` из `ErrorInfo`; обновлён `csstype`

### @types/react-dom `19.1.0 → 19.2.3`
- Синхронизирован с `@types/react` для React 19.2 DOM APIs
- Добавлена поддержка `SubmitEvent.submitter` в типах form-событий
- Исправления совместимости с TypeScript 6.0
