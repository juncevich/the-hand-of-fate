import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'
import { extractErrorMessage } from '@/lib/errors'

const MIN_PASSWORD = 8

/** Target of the link in the password reset email. */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const navigate = useNavigate()

  const reset = useMutation({
    mutationFn: () => authApi.resetPassword(token, password),
    onSuccess: () => {
      toast('Пароль изменён', 'Войдите с новым паролем')
      void navigate('/login', { replace: true })
    },
    onError: (e) => toast('Не удалось сменить пароль', extractErrorMessage(e), 'error'),
  })

  const tooShort = password.length > 0 && password.length < MIN_PASSWORD
  const mismatch = confirm.length > 0 && confirm !== password

  return (
    <AuthLayout subtitle="Новый пароль">
      <div className="glass p-6 space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="password">Новый пароль</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            placeholder="Минимум 8 символов"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {tooShort && <p className="text-xs text-red-500">Минимум {MIN_PASSWORD} символов</p>}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="confirm">Повторите пароль</Label>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
          {mismatch && <p className="text-xs text-red-500">Пароли не совпадают</p>}
        </div>
        <Button
          className="w-full"
          onClick={() => reset.mutate()}
          isLoading={reset.isPending}
          disabled={!token || password.length < MIN_PASSWORD || confirm !== password}
        >
          Сохранить пароль
        </Button>
      </div>
      <p className="text-center text-sm text-fate-muted mt-4">
        <Link to="/forgot-password" className="text-fate-gold hover:underline">
          Запросить новую ссылку
        </Link>
      </p>
    </AuthLayout>
  )
}
