import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { AxiosError } from 'axios'
import { LoginPage } from '../LoginPage'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    resendVerification: vi.fn(),
    silentRefresh: vi.fn().mockRejectedValue(new Error('no session')),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

const mockNavigate = vi.fn()

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders email, password fields and submit button', () => {
    render(<LoginPage />, { wrapper: createWrapper() })

    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Пароль')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Войти/i })).toBeInTheDocument()
  })

  it('submit button is disabled when fields are empty', () => {
    render(<LoginPage />, { wrapper: createWrapper() })

    expect(screen.getByRole('button', { name: /Войти/i })).toBeDisabled()
  })

  it('submit button is disabled when only email is filled', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'test@example.com')

    expect(screen.getByRole('button', { name: /Войти/i })).toBeDisabled()
  })

  it('submit button is enabled when both fields are filled', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123')

    expect(screen.getByRole('button', { name: /Войти/i })).not.toBeDisabled()
  })

  it('calls authApi.login with entered values on submit', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'tok',
      userId: '1',
      email: 'test@example.com',
      displayName: 'Test',
      avatarUrl: null,
    })

    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123')
    await user.click(screen.getByRole('button', { name: /Войти/i }))

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'test@example.com',
        password: 'password123',
      })
    })
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
  })

  it('shows error toast on login failure', async () => {
    const { authApi } = await import('@/api/auth')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(authApi.login).mockRejectedValueOnce(new Error('Unauthorized'))

    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'wrongpassword')
    await user.click(screen.getByRole('button', { name: /Войти/i }))

    await waitFor(() => {
      expect(toast).toHaveBeenCalledWith('Ошибка входа', 'Неверный email или пароль', 'error')
    })
  })

  it('password field is hidden by default', () => {
    render(<LoginPage />, { wrapper: createWrapper() })

    expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'password')
  })

  it('shows password when toggle button is clicked', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.click(screen.getByRole('button', { name: 'Показать пароль' }))

    expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'text')
  })

  it('hides password again on second click of toggle button', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.click(screen.getByRole('button', { name: 'Показать пароль' }))
    await user.click(screen.getByRole('button', { name: 'Скрыть пароль' }))

    expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'password')
  })

  it('submits on Enter key in the password field', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'tok',
      userId: '1',
      email: 'test@example.com',
      displayName: 'Test',
      avatarUrl: null,
    })

    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123{Enter}')

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalled()
    })
  })

  function problem(status: number, data: Record<string, unknown>) {
    const error = new AxiosError('Request failed', String(status))
    error.response = { data, status, statusText: '', headers: {}, config: error.config! }
    return error
  }

  it('offers to resend the link when the address is not confirmed yet', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.login).mockRejectedValueOnce(problem(403, { code: 'EMAIL_NOT_VERIFIED', title: 'Confirm' }))
    vi.mocked(authApi.resendVerification).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })
    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123{Enter}')

    expect(await screen.findByText('Сначала подтвердите адрес почты')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Отправить письмо ещё раз' }))
    await waitFor(() => expect(authApi.resendVerification).toHaveBeenCalledWith('test@example.com'))

    await user.click(screen.getByRole('button', { name: 'Вернуться ко входу' }))
    expect(screen.getByLabelText('Пароль')).toBeInTheDocument()
  })

  it('tells about the rate limit instead of wrong credentials', async () => {
    const { authApi } = await import('@/api/auth')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(authApi.login).mockRejectedValueOnce(problem(429, { title: 'Too many' }))

    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })
    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123{Enter}')

    await waitFor(() => expect(toast).toHaveBeenCalledWith('Слишком много попыток', 'Попробуйте немного позже', 'error'))
  })

  it('shows no demo credentials outside the mock mode', () => {
    render(<LoginPage />, { wrapper: createWrapper() })

    expect(screen.queryByRole('button', { name: /Мок-режим/ })).not.toBeInTheDocument()
  })

  it('fills in the demo credentials in the mock mode', async () => {
    vi.stubEnv('MODE', 'mock')
    try {
      const user = userEvent.setup()
      render(<LoginPage />, { wrapper: createWrapper() })

      await user.click(screen.getByRole('button', { name: /demo@example\.com \/ password/ }))

      expect(screen.getByLabelText('Email')).toHaveValue('demo@example.com')
      expect(screen.getByLabelText('Пароль')).toHaveValue('password')
    } finally {
      vi.unstubAllEnvs()
    }
  })

  it('links to password recovery', () => {
    render(<LoginPage />, { wrapper: createWrapper() })

    expect(screen.getByRole('link', { name: 'Забыли пароль?' })).toHaveAttribute('href', '/forgot-password')
  })
})
