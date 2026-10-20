import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import App from '../App'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'

vi.mock('@/auth/session', () => ({
  session: { restore: vi.fn() },
}))

vi.mock('@/pages/LoginPage', () => ({ LoginPage: () => <div>Login page</div> }))
vi.mock('@/pages/OidcCallbackPage', () => ({ OidcCallbackPage: () => <div>Callback page</div> }))
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

  it('restores an existing sign-in and shows the dashboard', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.restore).mockImplementationOnce(() => {
      useAuthStore.getState().setAuth({ accessToken: 't', userId: 'u1', email: 'a@x.y', displayName: 'Alice' })
      return Promise.resolve(true)
    })

    render(<App />)

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBe('t')
  })

  it('redirects to login without a sign-in', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.restore).mockResolvedValueOnce(false)

    render(<App />)

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })

  it('leaves the sign-in callback to complete its own sign-in', async () => {
    const { session } = await import('@/auth/session')
    window.history.pushState({}, '', '/oidc/callback?code=c&state=s')

    render(<App />)

    expect(await screen.findByText('Callback page')).toBeInTheDocument()
    expect(session.restore).not.toHaveBeenCalled()
  })

  it('redirects unknown routes to the protected root', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.restore).mockResolvedValueOnce(false)
    window.history.pushState({}, '', '/does-not-exist')

    render(<App />)

    expect(await screen.findByText('Login page')).toBeInTheDocument()
  })

  it('applies the light theme class to the document', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.restore).mockResolvedValueOnce(false)
    useThemeStore.setState({ theme: 'light' })

    render(<App />)

    await waitFor(() => expect(document.documentElement.classList.contains('light')).toBe(true))
  })
})
