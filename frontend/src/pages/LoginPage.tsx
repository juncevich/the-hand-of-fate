import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { ResendVerification } from '@/components/auth/ResendVerification'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'
import { errorCode } from '@/lib/errors'
import { Eye, EyeOff } from 'lucide-react'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [unverified, setUnverified] = useState(false)
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()

  const { mutate, isPending } = useMutation({
    mutationFn: () => authApi.login({ email, password }),
    onSuccess: (data) => {
      setAuth(data)
      void navigate('/')
    },
    onError: (error) => {
      // The backend answers this only after the password matched.
      if (errorCode(error) === 'EMAIL_NOT_VERIFIED') {
        setUnverified(true)
        return
      }
      if (axios.isAxiosError(error) && error.response?.status === 429) {
        toast('Слишком много попыток', 'Попробуйте немного позже', 'error')
        return
      }
      // Deliberately a fixed generic message rather than the backend's text:
      // login failures shouldn't reveal whether the account exists.
      toast('Ошибка входа', 'Неверный email или пароль', 'error')
    },
  })

  if (unverified) {
    return (
      <AuthLayout subtitle="Подтвердите email">
        <ResendVerification email={email} title="Сначала подтвердите адрес почты" />
        <p className="text-center text-sm text-fate-muted mt-4">
          <button type="button" onClick={() => setUnverified(false)} className="text-fate-gold hover:underline">
            Вернуться ко входу
          </button>
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout subtitle="Войдите в свой аккаунт">
      <div className="glass p-6 space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            placeholder="you@example.com"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="password">Пароль</Label>
            <Link to="/forgot-password" className="text-xs text-fate-gold hover:underline">
              Забыли пароль?
            </Link>
          </div>
          <div className="relative">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && mutate()}
              className="pr-10"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-fate-muted hover:text-fate-text transition-colors"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <Button
          className="w-full"
          onClick={() => mutate()}
          isLoading={isPending}
          disabled={!email || !password}
        >
          Войти
        </Button>
      </div>

      <p className="text-center text-sm text-fate-muted mt-4">
        Нет аккаунта?{' '}
        <Link to="/register" className="text-fate-gold hover:underline">
          Зарегистрироваться
        </Link>
      </p>
    </AuthLayout>
  )
}
