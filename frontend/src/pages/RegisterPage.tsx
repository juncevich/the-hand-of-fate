import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'
import { Moon, Sparkles, Sun } from 'lucide-react'
import { useThemeStore } from '@/store/themeStore'

type FieldErrors = Partial<Record<'email' | 'password' | 'displayName', string>>

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()

  const { theme, toggleTheme } = useThemeStore()

  const { mutate, isPending } = useMutation({
    mutationFn: () => authApi.register({ email, password, displayName }),
    onSuccess: (data) => {
      setAuth(data)
      navigate('/')
    },
    onError: (e: Error) => {
      if (axios.isAxiosError(e) && e.response?.data?.errors) {
        setFieldErrors(e.response.data.errors as FieldErrors)
      }
      const detail = axios.isAxiosError(e)
        ? (e.response?.data?.title ?? e.response?.data?.detail ?? e.message)
        : e.message
      toast('Ошибка регистрации', detail, 'error')
    },
  })

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-fate-bg)] px-4">
      <Button
        variant="ghost"
        size="icon"
        onClick={toggleTheme}
        aria-label="Toggle theme"
        className="absolute top-4 right-4"
      >
        {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
      </Button>

      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-96 h-96 bg-[var(--color-fate-gold)]/5 rounded-full blur-3xl" />
      </div>

      <div className="relative w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="w-16 h-16 rounded-2xl bg-[var(--color-fate-gold)]/15 border border-[var(--color-fate-gold)]/30 flex items-center justify-center mx-auto mb-4 glow-gold">
            <Sparkles className="w-8 h-8 text-[var(--color-fate-gold)]" />
          </div>
          <h1 className="text-2xl font-bold text-[var(--color-fate-text)]">The Hand of Fate</h1>
          <p className="text-sm text-[var(--color-fate-muted)] mt-1">Создайте аккаунт</p>
        </div>

        <div className="glass p-6 space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="displayName">Имя</Label>
            <Input
              id="displayName"
              placeholder="Иван Иванов"
              value={displayName}
              onChange={(e) => { setDisplayName(e.target.value); setFieldErrors((prev) => ({ ...prev, displayName: undefined })) }}
            />
            {fieldErrors.displayName && (
              <p className="text-xs text-red-500">{fieldErrors.displayName}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => { setEmail(e.target.value); setFieldErrors((prev) => ({ ...prev, email: undefined })) }}
            />
            {fieldErrors.email && (
              <p className="text-xs text-red-500">{fieldErrors.email}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="password">Пароль</Label>
            <Input
              id="password"
              type="password"
              placeholder="Минимум 8 символов"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setFieldErrors((prev) => ({ ...prev, password: undefined })) }}
              onKeyDown={(e) => e.key === 'Enter' && mutate()}
            />
            {fieldErrors.password && (
              <p className="text-xs text-red-500">{fieldErrors.password}</p>
            )}
          </div>

          <Button
            className="w-full"
            onClick={() => mutate()}
            isLoading={isPending}
            disabled={!email || !password || !displayName}
          >
            Зарегистрироваться
          </Button>
        </div>

        <p className="text-center text-sm text-[var(--color-fate-muted)] mt-4">
          Уже есть аккаунт?{' '}
          <Link to="/login" className="text-[var(--color-fate-gold)] hover:underline">
            Войти
          </Link>
        </p>
      </div>
    </div>
  )
}
