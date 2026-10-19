import { useState } from 'react'
import { Link } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { ResendVerification } from '@/components/auth/ResendVerification'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'
import { errorCode, extractErrorMessage, extractFieldErrors } from '@/lib/errors'

type FieldErrors = Partial<Record<'email' | 'password' | 'displayName', string>>

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null)

  const { mutate, isPending } = useMutation({
    mutationFn: () => authApi.register({ email, password, displayName }),
    // No session yet: the account signs in by following the mailed link.
    onSuccess: (data) => setRegisteredEmail(data.email),
    onError: (e: Error) => {
      setFieldErrors(
        errorCode(e) === 'EMAIL_ALREADY_REGISTERED' ? { email: extractErrorMessage(e) } : (extractFieldErrors(e) as FieldErrors),
      )
      toast('Ошибка регистрации', extractErrorMessage(e), 'error')
    },
  })

  if (registeredEmail) {
    return (
      <AuthLayout subtitle="Почти готово">
        <ResendVerification email={registeredEmail} title="Проверьте почту" />
      </AuthLayout>
    )
  }

  return (
    <AuthLayout subtitle="Создайте аккаунт">
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

      <p className="text-center text-sm text-fate-muted mt-4">
        Уже есть аккаунт?{' '}
        <Link to="/login" className="text-fate-gold hover:underline">
          Войти
        </Link>
      </p>
    </AuthLayout>
  )
}
