import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { KeyRound } from 'lucide-react'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from '@/components/ui/toaster'
import { errorCode, onMutationError } from '@/lib/errors'

const MIN_PASSWORD = 8

export function ChangePasswordCard() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const setAuth = useAuthStore((s) => s.setAuth)

  const change = useMutation({
    mutationFn: () => authApi.changePassword({ currentPassword: current, newPassword: next }),
    onSuccess: (data) => {
      // The backend ended every other session and returned a fresh one for this tab.
      setAuth(data)
      setCurrent('')
      setNext('')
      toast('Пароль изменён', 'На других устройствах нужно будет войти заново')
    },
    onError: (e) => {
      if (errorCode(e) === 'WRONG_PASSWORD') toast('Неверный текущий пароль', undefined, 'error')
      else onMutationError(e)
    },
  })

  return (
    <div className="glass p-6 mb-4">
      <div className="flex items-center gap-2 mb-4">
        <KeyRound className="w-5 h-5 text-fate-gold" />
        <h2 className="text-sm font-medium text-fate-muted uppercase tracking-wider">Пароль</h2>
      </div>
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="current-password">Текущий пароль</Label>
          <Input
            id="current-password"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="new-password">Новый пароль</Label>
          <Input
            id="new-password"
            type="password"
            autoComplete="new-password"
            placeholder="Минимум 8 символов"
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
        </div>
        <Button
          variant="outline"
          onClick={() => change.mutate()}
          isLoading={change.isPending}
          disabled={!current || next.length < MIN_PASSWORD}
        >
          Сменить пароль
        </Button>
      </div>
    </div>
  )
}
