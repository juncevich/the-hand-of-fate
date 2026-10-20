import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/store/authStore'

const oidc = vi.hoisted(() => {
  const loaded: Array<(user: { access_token: string }) => void> = []
  const manager = {
    signinSilent: vi.fn(),
    signinRedirect: vi.fn(),
    signinRedirectCallback: vi.fn(),
    signoutRedirect: vi.fn(),
    getUser: vi.fn(),
    events: { addUserLoaded: (cb: (user: { access_token: string }) => void) => loaded.push(cb) },
  }
  return { manager, loaded, settings: [] as unknown[] }
})

vi.mock('oidc-client-ts', () => ({
  UserManager: vi.fn(function (this: unknown, settings: unknown) {
    oidc.settings.push(settings)
    return oidc.manager
  }),
  InMemoryWebStorage: vi.fn(),
  WebStorageStateStore: vi.fn(),
}))
vi.mock('@/api/users', () => ({ usersApi: { getProfile: vi.fn() } }))

const profile = { userId: 'u1', email: 'kate@test.com', displayName: 'Kate', avatarUrl: null }

describe('keycloakSession', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
    const { usersApi } = await import('@/api/users')
    vi.mocked(usersApi.getProfile).mockResolvedValue(profile)
  })

  it('uses the SPA client on this origin, with tokens kept in memory and silent renewal', async () => {
    await import('../keycloak')
    expect(oidc.settings[0]).toMatchObject({
      authority: `${window.location.origin}/auth/realms/fate`,
      client_id: 'fate-spa',
      redirect_uri: `${window.location.origin}/oidc/callback`,
      silent_redirect_uri: `${window.location.origin}/oidc/silent.html`,
      post_logout_redirect_uri: `${window.location.origin}/login`,
      automaticSilentRenew: true,
    })
  })

  it('restores a sign-in silently and loads the profile', async () => {
    const { keycloakSession } = await import('../keycloak')
    oidc.manager.signinSilent.mockResolvedValueOnce({ access_token: 'at' })

    await expect(keycloakSession.restore()).resolves.toBe(true)

    expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, accessToken: 'at', displayName: 'Kate' })
  })

  it('restores nothing without a Keycloak session', async () => {
    const { keycloakSession } = await import('../keycloak')
    oidc.manager.signinSilent.mockRejectedValueOnce(new Error('login_required'))

    await expect(keycloakSession.restore()).resolves.toBe(false)
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('maps sign-in options to Keycloak parameters and keeps returnTo inside the app', async () => {
    const { keycloakSession } = await import('../keycloak')

    await keycloakSession.signIn({ returnTo: '/votes/1', register: true })
    await keycloakSession.signIn({ returnTo: '//evil.example', reauthenticate: true })
    await keycloakSession.signIn({ action: 'UPDATE_PASSWORD' })

    expect(oidc.manager.signinRedirect.mock.calls.map(([args]) => args)).toEqual([
      { state: { returnTo: '/votes/1' }, prompt: 'create', extraQueryParams: undefined },
      { state: { returnTo: '/' }, prompt: 'login', extraQueryParams: undefined },
      { state: { returnTo: '/' }, prompt: undefined, extraQueryParams: { kc_action: 'UPDATE_PASSWORD' } },
    ])
  })

  it('completes the sign-in and returns where the user wanted', async () => {
    const { keycloakSession } = await import('../keycloak')
    oidc.manager.signinRedirectCallback.mockResolvedValueOnce({ access_token: 'at', state: { returnTo: '/votes/7' } })

    await expect(keycloakSession.completeSignIn()).resolves.toBe('/votes/7')
    expect(useAuthStore.getState().accessToken).toBe('at')
  })

  it('shares one renewal between concurrent callers and takes the new token', async () => {
    const { keycloakSession } = await import('../keycloak')
    useAuthStore.getState().setAuth({ accessToken: 'old', ...profile })
    oidc.manager.signinSilent.mockResolvedValueOnce({ access_token: 'renewed' })

    const tokens = await Promise.all([keycloakSession.renew(), keycloakSession.renew()])

    expect(tokens).toEqual(['renewed', 'renewed'])
    expect(oidc.manager.signinSilent).toHaveBeenCalledOnce()
    expect(useAuthStore.getState().accessToken).toBe('renewed')
  })

  it('fails the renewal once the Keycloak session has ended', async () => {
    const { keycloakSession } = await import('../keycloak')
    oidc.manager.signinSilent.mockResolvedValueOnce(null)

    await expect(keycloakSession.renew()).rejects.toThrow('Signed out')
  })

  it('takes tokens renewed in the background', async () => {
    await import('../keycloak')
    useAuthStore.getState().setAuth({ accessToken: 'old', ...profile })

    oidc.loaded.forEach((cb) => cb({ access_token: 'background' }))

    expect(useAuthStore.getState().accessToken).toBe('background')
  })

  it('signs out here and in Keycloak', async () => {
    const { keycloakSession } = await import('../keycloak')
    useAuthStore.getState().setAuth({ accessToken: 'at', ...profile })
    oidc.manager.getUser.mockResolvedValueOnce({ id_token: 'idt' })

    await keycloakSession.signOut()

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(oidc.manager.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'idt' })
  })
})
