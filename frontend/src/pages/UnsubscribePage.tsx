import { Link, useSearchParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { usersApi } from '@/api/users'
import { AuthLayout } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/button'
import { toast } from '@/components/ui/toaster'

/**
 * Target of the unsubscribe link in notification emails. It only unsubscribes on a click:
 * mail scanners that prefetch links must not opt people out.
 */
export function UnsubscribePage() {
  const [params] = useSearchParams()
  const email = params.get('email') ?? ''
  const signature = params.get('sig') ?? ''

  const unsubscribe = useMutation({
    mutationFn: () => usersApi.unsubscribe(email, signature),
    onError: () => toast('Ссылка недействительна', 'Откройте её из последнего письма', 'error'),
  })

  return (
    <AuthLayout subtitle="Отписка от писем">
      <div className="glass p-6 space-y-4 text-center" role="status">
        {unsubscribe.isSuccess ? (
          <>
            <p className="font-medium text-fate-text">Вы отписались</p>
            <p className="text-sm text-fate-muted">
              На {email} больше не будут приходить приглашения и результаты голосований. Вернуть письма можно в
              настройках аккаунта.
            </p>
          </>
        ) : (
          <>
            <p className="text-sm text-fate-muted">
              Больше не присылать приглашения и результаты голосований на <span className="text-fate-text">{email}</span>?
            </p>
            <Button
              className="w-full"
              onClick={() => unsubscribe.mutate()}
              isLoading={unsubscribe.isPending}
              disabled={!email || !signature}
            >
              Отписаться
            </Button>
          </>
        )}
      </div>
      <p className="text-center text-sm text-fate-muted mt-4">
        <Link to="/" className="text-fate-gold hover:underline">На главную</Link>
      </p>
    </AuthLayout>
  )
}
