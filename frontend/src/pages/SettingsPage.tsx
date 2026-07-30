import { useTelegramLink } from '@/hooks/useTelegramLink'
import { Button } from '@/components/ui/button'
import { Bot, Copy, Link2, Link2Off } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

export function SettingsPage() {
  const { displayName, email } = useAuthStore()
  const { token, clearToken, getLinkToken, copyToken } = useTelegramLink()

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold text-fate-text mb-8">Настройки</h1>

      <div className="glass p-6 mb-4">
        <h2 className="text-sm font-medium text-fate-muted mb-4 uppercase tracking-wider">
          Профиль
        </h2>
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-full bg-fate-gold/15 border border-fate-gold/30 flex items-center justify-center text-lg font-semibold text-fate-gold">
            {displayName?.[0]?.toUpperCase()}
          </div>
          <div>
            <p className="font-medium text-fate-text">{displayName}</p>
            <p className="text-sm text-fate-muted">{email}</p>
          </div>
        </div>
      </div>

      <div className="glass p-6">
        <div className="flex items-center gap-2 mb-4">
          <Bot className="w-5 h-5 text-fate-gold" />
          <h2 className="text-sm font-medium text-fate-muted uppercase tracking-wider">
            Telegram бот
          </h2>
        </div>

        <p className="text-sm text-fate-muted mb-4">
          Подключите Telegram-аккаунт, чтобы получать уведомления о голосованиях и результатах прямо в мессенджер.
        </p>

        {!token ? (
          <Button
            variant="outline"
            onClick={() => getLinkToken.mutate()}
            isLoading={getLinkToken.isPending}
            className="flex items-center gap-2"
          >
            <Link2 className="w-4 h-4" />
            Получить код для привязки
          </Button>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-fate-text">
              Отправьте эту команду{' '}
              <a
                href={`https://t.me/${import.meta.env.VITE_BOT_USERNAME ?? 'YourBotUsername'}`}
                target="_blank"
                rel="noreferrer"
                className="text-fate-gold hover:underline"
              >
                @{import.meta.env.VITE_BOT_USERNAME ?? 'YourBotUsername'}
              </a>{' '}
              в Telegram:
            </p>
            <div className="flex items-center gap-2 bg-black/30 rounded-lg p-3 font-mono text-sm text-fate-gold">
              <span className="flex-1">/link {token}</span>
              <Button variant="ghost" size="icon" onClick={copyToken} className="h-6 w-6">
                <Copy className="w-4 h-4" />
              </Button>
            </div>
            <p className="text-xs text-fate-muted">Токен действителен 5 минут</p>
            <Button variant="ghost" size="sm" onClick={clearToken}>
              <Link2Off className="w-4 h-4" />
              Отмена
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
