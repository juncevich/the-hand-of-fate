import { StrictMode } from 'react'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { OidcCallbackPage } from '../OidcCallbackPage'

vi.mock('@/auth/session', () => ({ session: { completeSignIn: vi.fn() } }))

function LoginProbe() {
  const state = useLocation().state as { error?: string } | null
  return <div>Login page: {state?.error}</div>
}

function renderCallback() {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={['/oidc/callback?code=c&state=s']}>
        <Routes>
          <Route path="/oidc/callback" element={<OidcCallbackPage />} />
          <Route path="/votes/:id" element={<div>Vote page</div>} />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  )
}

describe('OidcCallbackPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('completes the sign-in once, even under StrictMode, and goes where the user wanted', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.completeSignIn).mockResolvedValueOnce('/votes/42')

    renderCallback()

    expect(await screen.findByText('Vote page')).toBeInTheDocument()
    expect(session.completeSignIn).toHaveBeenCalledOnce()
  })

  it('goes back to the login page when the sign-in cannot be completed', async () => {
    const { session } = await import('@/auth/session')
    vi.mocked(session.completeSignIn).mockRejectedValueOnce(new Error('invalid_grant'))

    renderCallback()

    expect(await screen.findByText(/Не удалось войти/)).toBeInTheDocument()
  })
})
