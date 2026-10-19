import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { TriangleAlert } from 'lucide-react'
import { authApi } from '@/api/auth'
import { votesApi } from '@/api/votes'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { toast } from '@/components/ui/toaster'
import { onMutationError } from '@/lib/errors'

export function DeleteAccountCard() {
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const navigate = useNavigate()
  const stats = useQuery({ queryKey: ['vote-stats'], queryFn: votesApi.stats, enabled: open })

  const remove = useMutation({
    mutationFn: () => authApi.deleteAccount(password),
    onSuccess: () => {
      clearAuth()
      toast('Аккаунт удалён')
      void navigate('/login', { replace: true })
    },
    onError: onMutationError,
  })

  const created = stats.data?.createdCount

  return (
    <div className="glass p-6 mt-4 border border-red-500/30">
      <div className="flex items-center gap-2 mb-2">
        <TriangleAlert className="w-5 h-5 text-red-400" />
        <h2 className="text-sm font-medium text-red-400 uppercase tracking-wider">Удаление аккаунта</h2>
      </div>
      <p className="text-sm text-fate-muted mb-4">
        Аккаунт, созданные вами голосования и их история будут удалены безвозвратно.
      </p>
      <Dialog open={open} onOpenChange={(value) => { setOpen(value); if (!value) setPassword('') }}>
        <DialogTrigger asChild>
          <Button variant="outline" className="border-red-500/40 text-red-400 hover:text-red-300">
            Удалить аккаунт
          </Button>
        </DialogTrigger>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Удалить аккаунт?</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-fate-muted" role="status">
              {created === undefined
                ? 'Считаем ваши голосования…'
                : `Будет удалено голосований, созданных вами: ${created}.`}{' '}
              В чужих голосованиях вас уберут из участников, а прошлые победы останутся без имени.
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="delete-password">Пароль для подтверждения</Label>
              <Input
                id="delete-password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setOpen(false)}>Отмена</Button>
              <Button
                className="bg-red-600 hover:bg-red-500 text-white"
                onClick={() => remove.mutate()}
                isLoading={remove.isPending}
                disabled={!password}
              >
                Удалить навсегда
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
