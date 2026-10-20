import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { Topbar } from '../Topbar'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'

vi.mock('@/auth/session', () => ({
  session: { signOut: vi.fn() },
}))

function renderTopbar() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<Topbar />} />
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/settings" element={<div>Settings page</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('Topbar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useThemeStore.setState({ theme: 'dark' })
    useAuthStore.getState().setAuth({ accessToken: 't', userId: 'u1', email: 'a@x.y', displayName: 'Alice' })
  })

  it('shows the current user name', () => {
    renderTopbar()
    expect(screen.getByText('Alice')).toBeInTheDocument()
  })

  it('toggles the theme', async () => {
    renderTopbar()

    await userEvent.click(screen.getByRole('button', { name: 'Toggle theme' }))

    expect(useThemeStore.getState().theme).toBe('light')
  })

  it('navigates to settings', async () => {
    renderTopbar()

    await userEvent.click(screen.getByRole('button', { name: 'Settings' }))

    expect(await screen.findByText('Settings page')).toBeInTheDocument()
  })

  it('signs out and goes to the login page', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.signOut).mockImplementationOnce(() => {
      useAuthStore.getState().clearAuth()
      return Promise.resolve()
    })
    renderTopbar()

    await userEvent.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(session.signOut).toHaveBeenCalledOnce()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('still goes to the login page when Keycloak cannot be reached', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.signOut).mockImplementationOnce(() => {
      useAuthStore.getState().clearAuth()
      return Promise.reject(new Error('offline'))
    })
    renderTopbar()

    await userEvent.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })
})
