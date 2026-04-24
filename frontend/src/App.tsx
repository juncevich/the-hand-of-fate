import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useEffect } from 'react'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'
import { authApi } from '@/api/auth'
import { Toaster } from '@/components/ui/toaster'
import { AppShell } from '@/components/layout/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { VoteDetailPage } from '@/pages/VoteDetailPage'
import { SettingsPage } from '@/pages/SettingsPage'

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  const setAuth = useAuthStore((s) => s.setAuth)
  const theme = useThemeStore((s) => s.theme)

  useEffect(() => {
    document.documentElement.classList.toggle('light', theme === 'light')
  }, [theme])

  // Attempt silent refresh on mount using the httpOnly cookie
  useEffect(() => {
    authApi
      .silentRefresh()
      .then((data) => setAuth(data))
      .catch(() => {
        /* not logged in — that's fine */
      })
  }, [setAuth])

  return (
    <BrowserRouter>
      {import.meta.env.DEV && (
        <div className="fixed top-3 right-3 z-[9999] pointer-events-none select-none">
          <span className="text-xs font-mono tracking-widest uppercase opacity-30 text-[var(--color-fate-gold)]">
            dev mode
          </span>
        </div>
      )}
      <Routes>
        <Route path="/login"    element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route
          path="/"
          element={
            <ProtectedRoute>
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
