import { useState } from 'react'
import { Link } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const request = useMutation({
    mutationFn: () => authApi.forgotPassword(email),
    onError: () => toast('Не удалось отправить запрос', 'Попробуйте немного позже', 'error'),
  })

  return (
    <AuthLayout subtitle="Восстановление пароля">
      {request.isSuccess ? (
        <div className="glass p-6 text-center space-y-2" role="status">
          <p className="font-medium text-fate-text">Проверьте почту</p>
          {/* Same answer whether or not the account exists. */}
          <p className="text-sm text-fate-muted">
            Если аккаунт с адресом {email} существует, мы отправили на него ссылку для сброса пароля.
            Она действует 30 минут.
          </p>
        </div>
      ) : (
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
              onKeyDown={(e) => e.key === 'Enter' && email && request.mutate()}
            />
          </div>
          <Button className="w-full" onClick={() => request.mutate()} isLoading={request.isPending} disabled={!email}>
            Отправить ссылку
          </Button>
        </div>
      )}
      <p className="text-center text-sm text-fate-muted mt-4">
        <Link to="/login" className="text-fate-gold hover:underline">
          Вернуться ко входу
        </Link>
      </p>
    </AuthLayout>
  )
}
