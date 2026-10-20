import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ExternalLink, KeyRound, LogOut } from 'lucide-react'
import { authApi } from '@/api/auth'
import { session } from '@/auth/session'
import { Button, buttonVariants } from '@/components/ui/button'
import { toast } from '@/components/ui/toaster'
import { onMutationError } from '@/lib/errors'
import { cn } from '@/lib/utils'

/** Password, linked accounts and sessions — kept by Keycloak, reached from here. */
export function AccountSecurityCard() {
  const [redirecting, setRedirecting] = useState(false)

  const changePassword = () => {
    setRedirecting(true)
    session.signIn({ action: 'UPDATE_PASSWORD', returnTo: '/settings' }).catch(() => {
      setRedirecting(false)
      toast('Сервис входа недоступен', 'Попробуйте немного позже', 'error')
    })
  }

  const logoutAll = useMutation({
    mutationFn: authApi.logoutAll,
    onSuccess: () => session.signOut(),
    onError: onMutationError,
  })

  return (
    <div className="glass p-6 mb-4">
      <div className="flex items-center gap-2 mb-4">
        <KeyRound className="w-5 h-5 text-fate-gold" />
        <h2 className="text-sm font-medium text-fate-muted uppercase tracking-wider">Вход и безопасность</h2>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={changePassword} isLoading={redirecting}>
          {/* Keycloak asks for the new password (or sets a first one after a social sign-in). */}
          Сменить пароль
        </Button>
        {session.accountUrl && (
          <a href={session.accountUrl} className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'flex items-center gap-2')}>
            <ExternalLink className="w-4 h-4" />
            Связанные аккаунты и сессии
          </a>
        )}
        <Button
          variant="ghost"
          size="sm"
          onClick={() => logoutAll.mutate()}
          isLoading={logoutAll.isPending}
          className="flex items-center gap-2"
        >
          <LogOut className="w-4 h-4" />
          Выйти на всех устройствах
        </Button>
      </div>
    </div>
  )
}
