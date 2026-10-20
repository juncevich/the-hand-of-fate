import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AxiosError } from 'axios'
import { AccountSecurityCard } from '@/components/settings/AccountSecurityCard'
import { DeleteAccountCard } from '@/components/settings/DeleteAccountCard'
import { EmailNotificationsCard } from '@/components/settings/EmailNotificationsCard'
import { UnsubscribePage } from '../UnsubscribePage'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({ authApi: { deleteAccount: vi.fn(), logoutAll: vi.fn() } }))
vi.mock('@/auth/session', () => ({
  session: { signIn: vi.fn(), signOut: vi.fn(), accountUrl: 'http://app.test/auth/realms/fate/account' },
}))
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

  it('shows how many votes go with the account, deletes it and signs out', async () => {
    const { authApi } = await import('@/api/auth')
    const { votesApi } = await import('@/api/votes')
    const { session } = await import('@/auth/session')
    vi.mocked(votesApi.stats).mockResolvedValue({ createdCount: 4 })
    vi.mocked(authApi.deleteAccount).mockResolvedValueOnce({} as never)
    vi.mocked(session.signOut).mockResolvedValueOnce()
    const user = userEvent.setup()

    renderAt('/settings', <DeleteAccountCard />, '/settings')
    await user.click(screen.getByRole('button', { name: 'Удалить аккаунт' }))
    expect(await screen.findByText(/созданных вами: 4/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Удалить навсегда' }))

    await waitFor(() => expect(session.signOut).toHaveBeenCalledOnce())
    expect(authApi.deleteAccount).toHaveBeenCalledOnce()
  })

  it('after an old sign-in, asks for the password in Keycloak and comes back to the open dialog', async () => {
    const { authApi } = await import('@/api/auth')
    const { votesApi } = await import('@/api/votes')
    const { session } = await import('@/auth/session')
    vi.mocked(votesApi.stats).mockResolvedValue({ createdCount: 0 })
    const stale = new AxiosError('Forbidden', '403')
    stale.response = { data: { code: 'REAUTHENTICATION_REQUIRED' }, status: 403, statusText: '', headers: {}, config: stale.config! }
    vi.mocked(authApi.deleteAccount).mockRejectedValueOnce(stale)
    vi.mocked(session.signIn).mockReturnValueOnce(new Promise(() => {}))
    const user = userEvent.setup()

    renderAt('/settings', <DeleteAccountCard />, '/settings')
    await user.click(screen.getByRole('button', { name: 'Удалить аккаунт' }))
    await user.click(screen.getByRole('button', { name: 'Удалить навсегда' }))

    await waitFor(() =>
      expect(session.signIn).toHaveBeenCalledWith({ reauthenticate: true, returnTo: '/settings?delete-account' }),
    )
    expect(session.signOut).not.toHaveBeenCalled()
  })

  it('opens the deletion dialog when coming back from signing in again', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.stats).mockResolvedValue({ createdCount: 1 })

    renderAt('/settings?delete-account', <DeleteAccountCard />, '/settings')

    expect(await screen.findByRole('button', { name: 'Удалить навсегда' })).toBeInTheDocument()
  })

  it('reaches Keycloak for the password and linked accounts, and logs out everywhere', async () => {
    const { authApi } = await import('@/api/auth')
    const { session } = await import('@/auth/session')
    vi.mocked(session.signIn).mockReturnValueOnce(new Promise(() => {}))
    vi.mocked(authApi.logoutAll).mockResolvedValueOnce({} as never)
    vi.mocked(session.signOut).mockResolvedValueOnce()
    const user = userEvent.setup()

    renderAt('/settings', <AccountSecurityCard />, '/settings')
    expect(screen.getByRole('link', { name: /Связанные аккаунты/ })).toHaveAttribute(
      'href',
      'http://app.test/auth/realms/fate/account',
    )
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))
    expect(session.signIn).toHaveBeenCalledWith({ action: 'UPDATE_PASSWORD', returnTo: '/settings' })

    await user.click(screen.getByRole('button', { name: /Выйти на всех устройствах/ }))
    await waitFor(() => expect(session.signOut).toHaveBeenCalledOnce())
    expect(authApi.logoutAll).toHaveBeenCalledOnce()
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
