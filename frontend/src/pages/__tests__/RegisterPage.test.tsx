import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError } from 'axios'
import { RegisterPage } from '../RegisterPage'

vi.mock('@/api/auth', () => ({
  authApi: {
    register: vi.fn(),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

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

async function fillForm(displayName: string, email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Имя'), displayName)
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Пароль'), password)
  return user
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders all form fields and the submit button', () => {
    render(<RegisterPage />, { wrapper: createWrapper() })

    expect(screen.getByLabelText('Имя')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Пароль')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Зарегистрироваться/i })).toBeInTheDocument()
  })

  it('submit button is disabled when fields are empty', () => {
    render(<RegisterPage />, { wrapper: createWrapper() })

    expect(screen.getByRole('button', { name: /Зарегистрироваться/i })).toBeDisabled()
  })

  it('submit button is disabled when only some fields are filled', async () => {
    const user = userEvent.setup()
    render(<RegisterPage />, { wrapper: createWrapper() })

    await user.type(screen.getByLabelText('Email'), 'ivan@example.com')

    expect(screen.getByRole('button', { name: /Зарегистрироваться/i })).toBeDisabled()
  })

  it('submit button is enabled when all fields are filled', async () => {
    render(<RegisterPage />, { wrapper: createWrapper() })

    await fillForm('Ivan', 'ivan@example.com', 'password123')

    expect(screen.getByRole('button', { name: /Зарегистрироваться/i })).not.toBeDisabled()
  })

  it('calls authApi.register with entered values on submit', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.register).mockResolvedValueOnce({
      accessToken: 'tok',
      refreshToken: 'refresh',
      userId: '1',
      email: 'ivan@example.com',
      displayName: 'Ivan',
    })

    render(<RegisterPage />, { wrapper: createWrapper() })
    const user = await fillForm('Ivan', 'ivan@example.com', 'password123')
    await user.click(screen.getByRole('button', { name: /Зарегистрироваться/i }))

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith({
        email: 'ivan@example.com',
        password: 'password123',
        displayName: 'Ivan',
      })
    })
  })

  it('shows field-level errors returned by the server', async () => {
    const { authApi } = await import('@/api/auth')

    const axiosError = new AxiosError('Validation failed', '400')
    axiosError.response = {
      data: { errors: { email: 'Email already registered' }, title: 'Validation failed' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: axiosError.config!,
    }
    vi.mocked(authApi.register).mockRejectedValueOnce(axiosError)

    render(<RegisterPage />, { wrapper: createWrapper() })
    const user = await fillForm('Ivan', 'taken@example.com', 'password123')
    await user.click(screen.getByRole('button', { name: /Зарегистрироваться/i }))

    expect(await screen.findByText('Email already registered')).toBeInTheDocument()
  })

  it('clears field error when user edits the field', async () => {
    const { authApi } = await import('@/api/auth')

    const axiosError = new AxiosError('Validation failed', '400')
    axiosError.response = {
      data: { errors: { email: 'Email already registered' }, title: 'Validation failed' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: axiosError.config!,
    }
    vi.mocked(authApi.register).mockRejectedValueOnce(axiosError)

    render(<RegisterPage />, { wrapper: createWrapper() })
    const user = await fillForm('Ivan', 'taken@example.com', 'password123')
    await user.click(screen.getByRole('button', { name: /Зарегистрироваться/i }))

    await screen.findByText('Email already registered')

    await user.type(screen.getByLabelText('Email'), 'x')

    expect(screen.queryByText('Email already registered')).not.toBeInTheDocument()
  })

  it('submits on Enter key in the password field', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.register).mockResolvedValueOnce({
      accessToken: 'tok',
      refreshToken: 'refresh',
      userId: '1',
      email: 'ivan@example.com',
      displayName: 'Ivan',
    })

    render(<RegisterPage />, { wrapper: createWrapper() })
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Имя'), 'Ivan')
    await user.type(screen.getByLabelText('Email'), 'ivan@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'password123{Enter}')

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalled()
    })
  })
})
