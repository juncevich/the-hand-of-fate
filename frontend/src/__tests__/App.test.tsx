import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import App from '../App'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'

vi.mock('@/api/auth', () => ({
  authApi: { silentRefresh: vi.fn(), logout: vi.fn() },
}))

vi.mock('@/pages/LoginPage', () => ({ LoginPage: () => <div>Login page</div> }))
vi.mock('@/pages/RegisterPage', () => ({ RegisterPage: () => <div>Register page</div> }))
vi.mock('@/pages/DashboardPage', () => ({ DashboardPage: () => <div>Dashboard page</div> }))
vi.mock('@/pages/VoteDetailPage', () => ({ VoteDetailPage: () => <div>Vote page</div> }))
vi.mock('@/pages/SettingsPage', () => ({ SettingsPage: () => <div>Settings page</div> }))
vi.mock('@/components/layout/BackendStatusIndicator', () => ({ BackendStatusIndicator: () => null }))

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
    useThemeStore.setState({ theme: 'dark' })
    window.history.pushState({}, '', '/')
  })

  it('restores the session via silent refresh and shows the dashboard', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.silentRefresh).mockResolvedValueOnce({
      accessToken: 't',
      userId: 'u1',
      email: 'a@x.y',
      displayName: 'Alice',
    })

    render(<App />)

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBe('t')
  })

  it('redirects to login when silent refresh fails', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.silentRefresh).mockRejectedValueOnce(new Error('401'))

    render(<App />)

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })

  it('redirects unknown routes to the protected root', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.silentRefresh).mockRejectedValueOnce(new Error('401'))
    window.history.pushState({}, '', '/does-not-exist')

    render(<App />)

    expect(await screen.findByText('Login page')).toBeInTheDocument()
  })

  it('applies the light theme class to the document', async () => {
    const { authApi } = await import('@/api/auth')
    vi.mocked(authApi.silentRefresh).mockRejectedValueOnce(new Error('401'))
    useThemeStore.setState({ theme: 'light' })

    render(<App />)

    await waitFor(() => expect(document.documentElement.classList.contains('light')).toBe(true))
  })
})
