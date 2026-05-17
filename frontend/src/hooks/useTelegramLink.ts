import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { telegramApi } from '@/api/telegram'
import { toast } from '@/components/ui/toaster'

export function useTelegramLink() {
  const [token, setToken] = useState<string | null>(null)

  const getLinkToken = useMutation({
    mutationFn: telegramApi.getLinkToken,
    onSuccess: (data) => setToken(data.token),
    onError: () => toast('Ошибка', 'Не удалось получить токен', 'error'),
  })

  const copyToken = () => {
    if (!token) return
    navigator.clipboard.writeText(`/link ${token}`)
    toast('Скопировано!', 'Отправьте эту команду боту')
  }

  return { token, clearToken: () => setToken(null), getLinkToken, copyToken }
}
