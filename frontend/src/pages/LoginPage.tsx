import { useState } from 'react'
import { Navigate, useLocation } from 'react-router'
import { LogIn, UserPlus } from 'lucide-react'
import { session } from '@/auth/session'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/button'
import { toast } from '@/components/ui/toaster'
import { useAuthStore } from '@/store/authStore'

interface LoginState {
  /** The protected page the user was sent here from. */
  from?: string
  /** Why the last sign-in did not complete. */
  error?: string
}

/**
 * The signed-out start page. Signing in and registering happen on Keycloak's pages (docs/adr/0001),
 * which come back to /oidc/callback.
 */
export function LoginPage() {
  const state = (useLocation().state ?? {}) as LoginState
  const [pending, setPending] = useState<'sign-in' | 'register' | null>(null)
  // A sign-in restored in the background (Keycloak's session cookie) needs no button.
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  const go = (register: boolean) => {
    setPending(register ? 'register' : 'sign-in')
    session.signIn({ returnTo: state.from, register }).catch(() => {
      setPending(null)
      toast('Сервис входа недоступен', 'Попробуйте немного позже', 'error')
    })
  }

  if (isAuthenticated) return <Navigate to={state.from ?? '/'} replace />

  return (
    <AuthLayout subtitle="Честный жребий для команды">
      <div className="glass p-6 space-y-3">
        {state.error && (
          <p role="alert" className="text-sm text-red-400">
            {state.error}
          </p>
        )}
        <Button className="w-full" onClick={() => go(false)} isLoading={pending === 'sign-in'} disabled={pending !== null}>
          <LogIn className="w-4 h-4" />
          Войти
        </Button>
        <Button
          variant="outline"
          className="w-full"
          onClick={() => go(true)}
          isLoading={pending === 'register'}
          disabled={pending !== null}
        >
          <UserPlus className="w-4 h-4" />
          Создать аккаунт
        </Button>
        {import.meta.env.MODE === 'mock' && (
          <p className="text-xs text-center text-fate-muted">Мок-режим: вход сразу под демо-аккаунтом</p>
        )}
      </div>
    </AuthLayout>
  )
}
