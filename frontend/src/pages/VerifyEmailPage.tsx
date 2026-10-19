import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { toast } from '@/components/ui/toaster'
import { errorCode, extractErrorMessage } from '@/lib/errors'

/** Target of the link in the verification email: confirms the address and signs in. */
export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(token ? null : 'В ссылке нет токена.')
  // The token is single-use: StrictMode's double effect run must not spend it twice.
  const started = useRef(false)

  useEffect(() => {
    if (!token || started.current) return
    started.current = true
    authApi
      .verifyEmail(token)
      .then((data) => {
        setAuth(data)
        toast('Email подтверждён', 'Добро пожаловать!')
        void navigate('/', { replace: true })
      })
      .catch((e: unknown) => {
        const message = extractErrorMessage(e)
        setError(errorCode(e) === 'TOKEN_EXPIRED' ? `${message}. Войдите, чтобы получить новую.` : message)
      })
  }, [token, setAuth, navigate])

  return (
    <AuthLayout subtitle="Подтверждение email">
      <div className="glass p-6 text-center space-y-4" role="status">
        {error ? (
          <>
            <p className="text-fate-text">{error}</p>
            <Link to="/login" className="text-fate-gold hover:underline text-sm">
              Перейти ко входу
            </Link>
          </>
        ) : (
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-fate-gold border-t-transparent mx-auto" />
        )}
      </div>
    </AuthLayout>
  )
}
