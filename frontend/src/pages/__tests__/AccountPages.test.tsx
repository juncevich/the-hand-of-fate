import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AxiosError } from 'axios'
import { VerifyEmailPage } from '../VerifyEmailPage'
import { ForgotPasswordPage } from '../ForgotPasswordPage'
import { ResetPasswordPage } from '../ResetPasswordPage'
import { ChangePasswordCard } from '@/components/settings/ChangePasswordCard'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({
  authApi: {
    verifyEmail: vi.fn(),
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
    changePassword: vi.fn(),
  },
}))

vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))

const session = { accessToken: 'tok', userId: 'u1', email: 'a@test.com', displayName: 'Ann', avatarUrl: null }

function problem(status: number, data: Record<string, unknown>) {
  const error = new AxiosError('Request failed', String(status))
  error.response = { data, status, statusText: '', headers: {}, config: error.config! }
  return error
}

function renderAt(path: string, element: React.ReactNode, route: string) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path={route} element={element} />
            <Route path="/" element={<div>Dashboard</div>} />
            <Route path="/login" element={<div>Login page</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </StrictMode>,
  )
}

describe('account pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
  })

  it('verifies the email once, even under StrictMode, and signs in', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.verifyEmail).mockResolvedValue(session)

    renderAt('/verify-email?token=abc', <VerifyEmailPage />, '/verify-email')

    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
    expect(authApi.verifyEmail).toHaveBeenCalledTimes(1)
    expect(authApi.verifyEmail).toHaveBeenCalledWith('abc')
    expect(useAuthStore.getState().accessToken).toBe('tok')
  })

  it('explains an expired verification link', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.verifyEmail).mockRejectedValue(problem(400, { code: 'TOKEN_EXPIRED' }))

    renderAt('/verify-email?token=old', <VerifyEmailPage />, '/verify-email')

    expect(await screen.findByText(/Срок действия ссылки истёк/)).toBeInTheDocument()
  })

  it('reports a link without a token without calling the backend', async () => {
    const { authApi } = await import('@/api/auth')

    renderAt('/verify-email', <VerifyEmailPage />, '/verify-email')

    expect(await screen.findByText('В ссылке нет токена.')).toBeInTheDocument()
    expect(authApi.verifyEmail).not.toHaveBeenCalled()
  })

  it('asks for a reset link and gives the same answer whatever the address', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.forgotPassword).mockResolvedValueOnce({} as never)
    const user = userEvent.setup()

    renderAt('/forgot-password', <ForgotPasswordPage />, '/forgot-password')
    await user.type(screen.getByLabelText('Email'), 'a@test.com')
    await user.click(screen.getByRole('button', { name: 'Отправить ссылку' }))

    expect(await screen.findByText(/Если аккаунт с адресом a@test.com существует/)).toBeInTheDocument()
    expect(authApi.forgotPassword).toHaveBeenCalledWith('a@test.com')
  })

  it('sets a new password from the reset link and goes to the login page', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.resetPassword).mockResolvedValueOnce({} as never)
    const user = userEvent.setup()

    renderAt('/reset-password?token=r1', <ResetPasswordPage />, '/reset-password')
    const save = screen.getByRole('button', { name: 'Сохранить пароль' })
    await user.type(screen.getByLabelText('Новый пароль'), 'new-password')
    await user.type(screen.getByLabelText('Повторите пароль'), 'new-passwor')
    expect(screen.getByText('Пароли не совпадают')).toBeInTheDocument()
    expect(save).toBeDisabled()
    await user.type(screen.getByLabelText('Повторите пароль'), 'd')
    await user.click(save)

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(authApi.resetPassword).toHaveBeenCalledWith('r1', 'new-password')
  })

  it('changes the password and keeps the fresh session', async () => {
    const { authApi } = await import('@/api/auth')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(authApi.changePassword)
      .mockRejectedValueOnce(problem(400, { code: 'WRONG_PASSWORD' }))
      .mockResolvedValueOnce({ ...session, accessToken: 'fresh' })
    const user = userEvent.setup()

    renderAt('/settings', <ChangePasswordCard />, '/settings')
    await user.type(screen.getByLabelText('Текущий пароль'), 'old-password')
    await user.type(screen.getByLabelText('Новый пароль'), 'new-password')
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))
    await waitFor(() => expect(toast).toHaveBeenCalledWith('Неверный текущий пароль', undefined, 'error'))

    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))
    await waitFor(() => expect(useAuthStore.getState().accessToken).toBe('fresh'))
    expect(authApi.changePassword).toHaveBeenLastCalledWith({ currentPassword: 'old-password', newPassword: 'new-password' })
  })
})
