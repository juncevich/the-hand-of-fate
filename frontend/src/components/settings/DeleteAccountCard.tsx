import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { TriangleAlert } from 'lucide-react'
import { authApi } from '@/api/auth'
import { votesApi } from '@/api/votes'
import { session } from '@/auth/session'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { toast } from '@/components/ui/toaster'
import { errorCode, onMutationError } from '@/lib/errors'

/** Back on /settings after signing in again, the dialog opens by itself. */
const CONFIRM_PARAM = 'delete-account'

export function DeleteAccountCard() {
  const [searchParams] = useSearchParams()
  const [open, setOpen] = useState(searchParams.has(CONFIRM_PARAM))
  const stats = useQuery({ queryKey: ['vote-stats'], queryFn: votesApi.stats, enabled: open })

  const remove = useMutation({
    mutationFn: authApi.deleteAccount,
    onSuccess: async () => {
      toast('Аккаунт удалён')
      // The Keycloak account goes too (the backend deletes it); end its session here.
      await session.signOut()
    },
    onError: (error) => {
      // The deletion needs a fresh sign-in: confirm with the password, then come back here.
      if (errorCode(error) === 'REAUTHENTICATION_REQUIRED') {
        void session.signIn({ reauthenticate: true, returnTo: `/settings?${CONFIRM_PARAM}` })
        return
      }
      onMutationError(error)
    },
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
      <Dialog open={open} onOpenChange={setOpen}>
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
            <p className="text-xs text-fate-muted">
              Если вы входили давно, сначала попросим ввести пароль ещё раз.
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setOpen(false)}>Отмена</Button>
              <Button
                className="bg-red-600 hover:bg-red-500 text-white"
                onClick={() => remove.mutate()}
                isLoading={remove.isPending}
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
