import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { telegramApi } from '@/api/telegram'
import { Button } from '@/components/ui/button'
import { toast } from '@/components/ui/toaster'
import { Bot, Copy, Link2, Link2Off } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

export function SettingsPage() {
  const [token, setToken] = useState<string | null>(null)
  const { displayName, email } = useAuthStore()

  const getLinkTokenMutation = useMutation({
    mutationFn: telegramApi.getLinkToken,
    onSuccess: (data) => setToken(data.token),
    onError: () => toast('Ошибка', 'Не удалось получить токен', 'error'),
  })

  const copyToken = () => {
    if (token) {
      navigator.clipboard.writeText(`/link ${token}`)
      toast('Скопировано!', 'Отправьте эту команду боту')
    }
  }

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold text-[var(--color-fate-text)] mb-8">Настройки</h1>

      {/* Profile */}
      <div className="glass p-6 mb-4">
        <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-4 uppercase tracking-wider">
          Профиль
        </h2>
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-full bg-[var(--color-fate-gold)]/15 border border-[var(--color-fate-gold)]/30 flex items-center justify-center text-lg font-semibold text-[var(--color-fate-gold)]">
            {displayName?.[0]?.toUpperCase()}
          </div>
          <div>
            <p className="font-medium text-[var(--color-fate-text)]">{displayName}</p>
            <p className="text-sm text-[var(--color-fate-muted)]">{email}</p>
          </div>
        </div>
      </div>

      {/* Telegram */}
      <div className="glass p-6">
        <div className="flex items-center gap-2 mb-4">
          <Bot className="w-5 h-5 text-[var(--color-fate-gold)]" />
          <h2 className="text-sm font-medium text-[var(--color-fate-muted)] uppercase tracking-wider">
            Telegram бот
          </h2>
        </div>

        <p className="text-sm text-[var(--color-fate-muted)] mb-4">
          Подключите Telegram-аккаунт, чтобы получать уведомления о голосованиях и результатах
          прямо в мессенджер.
        </p>

        {!token ? (
          <Button
            variant="outline"
            onClick={() => getLinkTokenMutation.mutate()}
            isLoading={getLinkTokenMutation.isPending}
            className="flex items-center gap-2"
          >
            <Link2 className="w-4 h-4" />
            Получить код для привязки
          </Button>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-[var(--color-fate-text)]">
              Отправьте эту команду{' '}
              <a
                href="https://t.me/YourBotUsername"
                target="_blank"
                rel="noreferrer"
                className="text-[var(--color-fate-gold)] hover:underline"
              >
                @YourBotUsername
              </a>{' '}
              в Telegram:
            </p>
            <div className="flex items-center gap-2 bg-black/30 rounded-lg p-3 font-mono text-sm text-[var(--color-fate-gold)]">
              <span className="flex-1">/link {token}</span>
              <button onClick={copyToken} className="text-[var(--color-fate-muted)] hover:text-[var(--color-fate-gold)]">
                <Copy className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-[var(--color-fate-muted)]">Токен действителен 5 минут</p>
            <Button variant="ghost" size="sm" onClick={() => setToken(null)}>
              <Link2Off className="w-4 h-4" />
              Отмена
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
