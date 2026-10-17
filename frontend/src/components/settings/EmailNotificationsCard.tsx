import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { usersApi } from '@/api/users'
import { onMutationError } from '@/lib/errors'

const QUERY_KEY = ['email-preferences']

export function EmailNotificationsCard() {
  const queryClient = useQueryClient()
  const { data } = useQuery({ queryKey: QUERY_KEY, queryFn: usersApi.getEmailPreferences })
  const update = useMutation({
    mutationFn: usersApi.updateEmailPreferences,
    onSuccess: (prefs) => queryClient.setQueryData(QUERY_KEY, prefs),
    onError: onMutationError,
  })
  const enabled = data?.notificationsEnabled ?? true

  return (
    <div className="glass p-6 mb-4">
      <div className="flex items-center gap-2 mb-4">
        <Mail className="w-5 h-5 text-fate-gold" />
        <h2 className="text-sm font-medium text-fate-muted uppercase tracking-wider">Письма</h2>
      </div>
      <label className="flex items-start gap-3 cursor-pointer">
        <input
          type="checkbox"
          className="mt-1 accent-fate-gold"
          checked={enabled}
          disabled={!data || update.isPending}
          onChange={(e) => update.mutate(e.target.checked)}
        />
        <span>
          <span className="block text-sm text-fate-text">Приглашения и результаты голосований</span>
          <span className="block text-xs text-fate-muted">
            Письма о подтверждении адреса и сбросе пароля приходят всегда
          </span>
        </span>
      </label>
    </div>
  )
}
