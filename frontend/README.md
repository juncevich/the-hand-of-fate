# Frontend

React SPA for The Hand of Fate — vote management and account settings.

## Tech Stack

| Library              | Version |
|----------------------|---------|
| React                | 19.2.5  |
| TypeScript           | 6.0.3   |
| Vite                 | 8.0.8   |
| Tailwind CSS         | 4.2.2   |
| shadcn/ui + Radix UI | —       |
| TanStack Query       | 5.99.0  |
| Zustand              | 5.0.12  |
| Axios                | 1.15.0  |
| React Router         | 7.14.1  |
| Vitest + Testing Library | —   |

## Page Routing

```
/login          LoginPage
/register       RegisterPage
/ (protected)
  ├── /             DashboardPage    — vote list, create vote modal
  ├── /votes/:id    VoteDetailPage   — draw, participants/options, history tabs
  └── /settings     SettingsPage     — Telegram linking, password change, logout
```

## Project Structure

```
src/
├── api/
│   ├── client.ts        Axios instance + 401 interceptor with retry queue
│   ├── auth.ts          register, login, refresh, logout
│   ├── votes.ts         vote CRUD, draw, history, options
│   └── telegram.ts      link token
├── pages/
│   ├── LoginPage.tsx
│   ├── RegisterPage.tsx
│   ├── DashboardPage.tsx     Paginated vote list, create vote modal
│   ├── VoteDetailPage.tsx    Draw button, participants/options tabs, history
│   └── SettingsPage.tsx      Telegram linking, theme, logout
├── components/
│   ├── ui/               shadcn/ui base components
│   ├── vote/             VoteCard, CreateVoteModal, DrawButton, VoteHistory
│   └── layout/           AppShell, Header
├── store/
│   ├── authStore.ts      Zustand: user, accessToken, isAuthenticated
│   └── themeStore.ts     Zustand: dark/light (persisted to localStorage)
├── types/
│   └── vote.ts           TypeScript interfaces for the vote domain
└── lib/
    └── utils.ts          cn() and shared helpers
```

## Auth Flow

```
App mounts
    │
    ▼
silentRefresh() ──success──► restore session (accessToken in memory)
    │
    └─ failure ──► stay logged out (no error shown)

Any request → 401
    │
    ▼
interceptor calls /api/v1/auth/refresh
    ├── queues concurrent requests
    ├── success ──► retry all queued requests with new token
    └── failure ──► redirect to /login
```

- `accessToken` lives in Zustand memory only (cleared on page refresh → silentRefresh restores it)
- `refreshToken` is an httpOnly cookie set by the backend

## Scripts

```bash
npm install
npm run dev          # Vite dev server on :3000, proxies /api → :8080
npm run dev:mock     # mock API plugin — no backend needed
npm run build        # production build → dist/
npm run preview      # preview production build locally
npm test             # run Vitest once
npm run test:watch   # Vitest watch mode
npm run lint         # ESLint
```

## Testing Patterns

- Wrap each test with a fresh `QueryClient` (retry: false) inside `QueryClientProvider` + `MemoryRouter`
- Mock API modules at the top of test files:
  ```ts
  vi.mock('@/api/votes', () => ({ votesApi: { getVotes: vi.fn() } }))
  ```
- Mock toaster: `vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))`
- Use `userEvent.setup()` for interactions; prefer `getByLabelText` / `getByRole` over test IDs
- Pages that use `useParams` need a route wrapper:
  ```tsx
  <Routes><Route path="/votes/:id" element={<VoteDetailPage />} /></Routes>
  ```
  with `initialEntries={['/votes/123']}` on `MemoryRouter`
