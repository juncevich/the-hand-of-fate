import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { Topbar } from '../Topbar'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'

vi.mock('@/api/auth', () => ({
  authApi: { logout: vi.fn() },
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

  it('logs out, clears auth and redirects to login', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.logout).mockResolvedValueOnce({} as never)
    renderTopbar()

    const buttons = screen.getAllByRole('button')
    await userEvent.click(buttons[buttons.length - 1])

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(authApi.logout).toHaveBeenCalledOnce()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('still logs out locally when the logout request fails', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.logout).mockRejectedValueOnce(new Error('offline'))
    renderTopbar()

    const buttons = screen.getAllByRole('button')
    await userEvent.click(buttons[buttons.length - 1])

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })
})
