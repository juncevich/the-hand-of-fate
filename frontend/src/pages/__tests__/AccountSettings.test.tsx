import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AxiosError } from 'axios'
import { DeleteAccountCard } from '@/components/settings/DeleteAccountCard'
import { EmailNotificationsCard } from '@/components/settings/EmailNotificationsCard'
import { UnsubscribePage } from '../UnsubscribePage'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({ authApi: { deleteAccount: vi.fn() } }))
vi.mock('@/api/votes', () => ({ votesApi: { stats: vi.fn() } }))
vi.mock('@/api/users', () => ({
  usersApi: { getEmailPreferences: vi.fn(), updateEmailPreferences: vi.fn(), unsubscribe: vi.fn() },
}))
vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))

function renderAt(path: string, element: React.ReactNode, route: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={route} element={element} />
          <Route path="/login" element={<div>Login page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('account settings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setAuth({ accessToken: 't', userId: 'u1', email: 'a@test.com', displayName: 'Ann' })
  })

  it('toggles notification emails', async () => {
    const { usersApi } = await import('@/api/users')
    vi.mocked(usersApi.getEmailPreferences).mockResolvedValue({ notificationsEnabled: true })
    vi.mocked(usersApi.updateEmailPreferences).mockResolvedValueOnce({ notificationsEnabled: false })
    const user = userEvent.setup()

    renderAt('/settings', <EmailNotificationsCard />, '/settings')
    const checkbox = await screen.findByRole('checkbox', { name: /Приглашения и результаты/ })
    await waitFor(() => expect(checkbox).toBeChecked())
    await user.click(checkbox)

    await waitFor(() => expect(checkbox).not.toBeChecked())
    expect(vi.mocked(usersApi.updateEmailPreferences).mock.calls[0][0]).toBe(false)
  })

  it('shows how many votes go with the account and deletes it with the password', async () => {
    const { authApi } = await import('@/api/auth')
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.stats).mockResolvedValue({ createdCount: 4 })
    const wrong = new AxiosError('Bad', '400')
    wrong.response = { data: { code: 'WRONG_PASSWORD' }, status: 400, statusText: '', headers: {}, config: wrong.config! }
    vi.mocked(authApi.deleteAccount).mockRejectedValueOnce(wrong).mockResolvedValueOnce({} as never)
    const user = userEvent.setup()

    renderAt('/settings', <DeleteAccountCard />, '/settings')
    await user.click(screen.getByRole('button', { name: 'Удалить аккаунт' }))
    expect(await screen.findByText(/созданных вами: 4/)).toBeInTheDocument()
    const confirm = screen.getByRole('button', { name: 'Удалить навсегда' })
    expect(confirm).toBeDisabled()

    await user.type(screen.getByLabelText('Пароль для подтверждения'), 'guess')
    await user.click(confirm)
    await waitFor(() => expect(toast).toHaveBeenCalledWith('Неверный пароль', undefined, 'error'))
    expect(useAuthStore.getState().isAuthenticated).toBe(true)

    await user.click(confirm)
    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(authApi.deleteAccount).toHaveBeenLastCalledWith('guess')
  })

  it('unsubscribes only when asked, not on opening the link', async () => {
    const { usersApi } = await import('@/api/users')
    vi.mocked(usersApi.unsubscribe).mockResolvedValueOnce({} as never)
    const user = userEvent.setup()

    renderAt('/unsubscribe?email=a%40test.com&sig=abc', <UnsubscribePage />, '/unsubscribe')
    expect(screen.getByText('a@test.com')).toBeInTheDocument()
    expect(usersApi.unsubscribe).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Отписаться' }))

    expect(await screen.findByText('Вы отписались')).toBeInTheDocument()
    expect(usersApi.unsubscribe).toHaveBeenCalledWith('a@test.com', 'abc')
  })
})
