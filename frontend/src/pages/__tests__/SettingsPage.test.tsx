import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { SettingsPage } from '../SettingsPage'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/telegram', () => ({
  telegramApi: { getLinkToken: vi.fn() },
}))

vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setAuth({ accessToken: 't', userId: 'u1', email: 'alice@example.com', displayName: 'alice' })
  })

  it('shows the profile with avatar initial', () => {
    renderPage()

    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('A')).toBeInTheDocument()
  })

  it('requests a link token and shows the /link command', async () => {
    const { telegramApi } = await import('@/api/telegram')
    vi.mocked(telegramApi.getLinkToken).mockResolvedValueOnce({ token: 'abc123', expiresAt: '2026-01-01T00:00:00Z' })
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /Получить код для привязки/i }))

    expect(await screen.findByText('/link abc123')).toBeInTheDocument()
    expect(screen.getByText('Токен действителен 5 минут')).toBeInTheDocument()
  })

  it('copies the command to the clipboard', async () => {
    const { telegramApi } = await import('@/api/telegram')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(telegramApi.getLinkToken).mockResolvedValueOnce({ token: 'abc123', expiresAt: '2026-01-01T00:00:00Z' })
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    renderPage()

    await user.click(screen.getByRole('button', { name: /Получить код для привязки/i }))
    const command = await screen.findByText('/link abc123')
    await user.click(command.parentElement!.querySelector('button')!)

    expect(writeText).toHaveBeenCalledWith('/link abc123')
    expect(toast).toHaveBeenCalledWith('Скопировано!', 'Отправьте эту команду боту')
  })

  it('returns to the initial state on cancel', async () => {
    const { telegramApi } = await import('@/api/telegram')
    vi.mocked(telegramApi.getLinkToken).mockResolvedValueOnce({ token: 'abc123', expiresAt: '2026-01-01T00:00:00Z' })
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /Получить код для привязки/i }))
    await userEvent.click(await screen.findByRole('button', { name: /Отмена/i }))

    expect(screen.queryByText('/link abc123')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Получить код для привязки/i })).toBeInTheDocument()
  })

  it('shows an error toast when the token request fails', async () => {
    const { telegramApi } = await import('@/api/telegram')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(telegramApi.getLinkToken).mockRejectedValueOnce(new Error('offline'))
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /Получить код для привязки/i }))

    await waitFor(() => expect(toast).toHaveBeenCalledWith('Ошибка', 'Не удалось получить токен', 'error'))
  })
})
