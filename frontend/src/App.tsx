import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router'
import { useEffect, useState, type ReactNode } from 'react'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'
import { session } from '@/auth/session'
import { Toaster } from '@/components/ui/toaster'
import { AppShell } from '@/components/layout/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { OidcCallbackPage } from '@/pages/OidcCallbackPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { VoteDetailPage } from '@/pages/VoteDetailPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { UnsubscribePage } from '@/pages/UnsubscribePage'
import { BackendStatusIndicator } from '@/components/layout/BackendStatusIndicator'

const CALLBACK_PATH = '/oidc/callback'

function ProtectedRoute({ children, isAuthReady }: { children: ReactNode; isAuthReady: boolean }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const location = useLocation()
  if (!isAuthReady) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-fate-gold border-t-transparent" />
      </div>
    )
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <>{children}</>
}

export default function App() {
  const theme = useThemeStore((s) => s.theme)
  // The sign-in callback completes a sign-in of its own; there is nothing to restore there.
  const [isAuthReady, setIsAuthReady] = useState(() => window.location.pathname === CALLBACK_PATH)

  useEffect(() => {
    document.documentElement.classList.toggle('light', theme === 'light')
  }, [theme])

  // Picks up an existing Keycloak sign-in without a redirect (its session cookie); none is fine.
  useEffect(() => {
    if (window.location.pathname === CALLBACK_PATH) return
    let active = true
    void session.restore().finally(() => { if (active) setIsAuthReady(true) })
    return () => { active = false }
  }, [])

  return (
    <BrowserRouter>
      {import.meta.env.DEV && (
        <div className="fixed top-3 right-3 z-[9999] pointer-events-none select-none flex items-center gap-2">
          <BackendStatusIndicator />
          <span className="text-xs font-mono tracking-widest uppercase opacity-30 text-fate-gold">
            dev mode
          </span>
        </div>
      )}
      <Routes>
        <Route path="/login"    element={<LoginPage />} />
        <Route path={CALLBACK_PATH} element={<OidcCallbackPage />} />
        <Route path="/unsubscribe" element={<UnsubscribePage />} />

        <Route
          path="/"
          element={
            <ProtectedRoute isAuthReady={isAuthReady}>
              <AppShell />
            </ProtectedRoute>
          }
        >
          <Route index          element={<DashboardPage />} />
          <Route path="votes/:id" element={<VoteDetailPage />} />
          <Route path="settings"  element={<SettingsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Toaster />
    </BrowserRouter>
  )
}
