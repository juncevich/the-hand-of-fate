import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'
import { Sparkles } from 'lucide-react'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()

  const { mutate, isPending } = useMutation({
    mutationFn: () => authApi.login({ email, password }),
    onSuccess: (data) => {
      setAuth(data)
      navigate('/')
    },
    onError: () => toast('Ошибка входа', 'Неверный email или пароль', 'error'),
  })

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-fate-bg)] px-4">
      {/* Background glow */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-96 h-96 bg-[var(--color-fate-gold)]/5 rounded-full blur-3xl" />
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-64 h-64 bg-purple-500/5 rounded-full blur-2xl" />
      </div>

      <div className="relative w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="w-16 h-16 rounded-2xl bg-[var(--color-fate-gold)]/15 border border-[var(--color-fate-gold)]/30 flex items-center justify-center mx-auto mb-4 glow-gold">
            <Sparkles className="w-8 h-8 text-[var(--color-fate-gold)]" />
          </div>
          <h1 className="text-2xl font-bold text-[var(--color-fate-text)]">The Hand of Fate</h1>
          <p className="text-sm text-[var(--color-fate-muted)] mt-1">Войдите в свой аккаунт</p>
        </div>

        {/* Form */}
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
            <Label htmlFor="password">Пароль</Label>
            <Input
              id="password"
              type="password"
              placeholder="••••••••"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && mutate()}
            />
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

        <p className="text-center text-sm text-[var(--color-fate-muted)] mt-4">
          Нет аккаунта?{' '}
          <Link to="/register" className="text-[var(--color-fate-gold)] hover:underline">
            Зарегистрироваться
          </Link>
        </p>
      </div>
    </div>
  )
}
