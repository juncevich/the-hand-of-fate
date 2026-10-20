import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { session } from '@/auth/session'

/** Where Keycloak sends the browser back after signing in: finishes the sign-in and moves on. */
export function OidcCallbackPage() {
  const navigate = useNavigate()
  // The authorization code is single-use; StrictMode's second effect run must not redeem it again.
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    session
      .completeSignIn()
      .then((returnTo) => navigate(returnTo, { replace: true }))
      .catch(() =>
        navigate('/login', { replace: true, state: { error: 'Не удалось войти. Попробуйте ещё раз.' } })
      )
  }, [navigate])

  return (
    <div className="flex h-screen items-center justify-center" role="status" aria-label="Вход…">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-fate-gold border-t-transparent" />
    </div>
  )
}
