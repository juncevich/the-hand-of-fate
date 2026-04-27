import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { LoginPage } from '../LoginPage'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    silentRefresh: vi.fn().mockRejectedValue(new Error('no session')),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => vi.fn() }
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
      refreshToken: 'refresh',
      userId: '1',
      email: 'test@example.com',
      displayName: 'Test',
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

  it('submits on Enter key in the password field', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'tok',
      refreshToken: 'refresh',
      userId: '1',
      email: 'test@example.com',
      displayName: 'Test',
    })

    const user = userEvent.setup()
    render(<LoginPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'test@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123{Enter}')

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalled()
    })
  })
})
