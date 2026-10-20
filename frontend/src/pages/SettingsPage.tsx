import { useRef } from 'react'
import { useTelegramLink } from '@/hooks/useTelegramLink'
import { useAvatar } from '@/hooks/useAvatar'
import { AVATAR_ACCEPT } from '@/api/users'
import { Button } from '@/components/ui/button'
import { UserAvatar } from '@/components/UserAvatar'
import { Bot, Copy, ImageUp, Link2, Link2Off, Trash2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { AccountSecurityCard } from '@/components/settings/AccountSecurityCard'
import { DeleteAccountCard } from '@/components/settings/DeleteAccountCard'
import { EmailNotificationsCard } from '@/components/settings/EmailNotificationsCard'

export function SettingsPage() {
  const { displayName, email, avatarUrl } = useAuthStore()
  const { token, clearToken, getLinkToken, copyToken } = useTelegramLink()
  const { upload, remove, selectFile } = useAvatar()
  const fileInput = useRef<HTMLInputElement>(null)

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold text-fate-text mb-8">Настройки</h1>

      <div className="glass p-6 mb-4">
        <h2 className="text-sm font-medium text-fate-muted mb-4 uppercase tracking-wider">
          Профиль
        </h2>
        <div className="flex items-center gap-3">
          <UserAvatar avatarUrl={avatarUrl} displayName={displayName} className="w-12 h-12 text-lg" />
          <div className="min-w-0">
            <p className="font-medium text-fate-text">{displayName}</p>
            <p className="text-sm text-fate-muted truncate">{email}</p>
          </div>
        </div>

        <input
          ref={fileInput}
          type="file"
          accept={AVATAR_ACCEPT}
          aria-label="Файл аватара"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) selectFile(file)
            // Reset so choosing the same file again still fires onChange
            e.target.value = ''
          }}
        />
        <div className="flex flex-wrap gap-2 mt-4">
          <Button
            variant="outline"
            size="sm"
            onClick={() => fileInput.current?.click()}
            isLoading={upload.isPending}
            className="flex items-center gap-2"
          >
            <ImageUp className="w-4 h-4" />
            {avatarUrl ? 'Сменить аватар' : 'Загрузить аватар'}
          </Button>
          {avatarUrl && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => remove.mutate()}
              isLoading={remove.isPending}
              className="flex items-center gap-2"
            >
              <Trash2 className="w-4 h-4" />
              Удалить
            </Button>
          )}
        </div>
        <p className="text-xs text-fate-muted mt-2">PNG, JPEG или GIF до 2 МБ</p>
      </div>

      <AccountSecurityCard />

      <EmailNotificationsCard />

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

      <DeleteAccountCard />
    </div>
  )
}
