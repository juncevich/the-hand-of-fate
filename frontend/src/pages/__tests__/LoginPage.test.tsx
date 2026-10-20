import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { LoginPage } from '../LoginPage'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/auth/session', () => ({ session: { signIn: vi.fn() } }))
vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))

function renderLogin(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>Dashboard page</div>} />
        <Route path="/votes/:id" element={<div>Vote page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
  })

  it('signs in through Keycloak, coming back to the page the user wanted', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.signIn).mockReturnValueOnce(new Promise(() => {}))   // the browser leaves the page
    const user = userEvent.setup()

    renderLogin({ from: '/votes/42' })
    await user.click(screen.getByRole('button', { name: 'Войти' }))

    expect(session.signIn).toHaveBeenCalledWith({ returnTo: '/votes/42', register: false })
    expect(screen.getByRole('button', { name: 'Создать аккаунт' })).toBeDisabled()
  })

  it('opens Keycloak registration', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.signIn).mockReturnValueOnce(new Promise(() => {}))
    const user = userEvent.setup()

    renderLogin()
    await user.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

    expect(session.signIn).toHaveBeenCalledWith({ returnTo: undefined, register: true })
  })

  it('says so when Keycloak cannot be reached and lets the user try again', async () => {
    const { session } = await import('@/auth/session')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(session.signIn).mockRejectedValueOnce(new Error('offline'))
    const user = userEvent.setup()

    renderLogin()
    await user.click(screen.getByRole('button', { name: 'Войти' }))

    expect(toast).toHaveBeenCalledWith('Сервис входа недоступен', 'Попробуйте немного позже', 'error')
    expect(screen.getByRole('button', { name: 'Войти' })).toBeEnabled()
  })

  it('shows why the last sign-in did not complete', () => {
    renderLogin({ error: 'Не удалось войти. Попробуйте ещё раз.' })

    expect(screen.getByRole('alert')).toHaveTextContent('Не удалось войти')
  })

  it('moves on once a sign-in is restored in the background', () => {
    useAuthStore.getState().setAuth({ accessToken: 't', userId: 'u1', email: 'a@test.com', displayName: 'Ann' })

    renderLogin({ from: '/votes/42' })

    expect(screen.getByText('Vote page')).toBeInTheDocument()
  })
})
