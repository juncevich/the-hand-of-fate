import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import { useAuthStore } from '@/store/authStore'
import type { AuthSession } from './session'
import { establishSession, safeReturnTo } from './establish'
import { OIDC_AUTHORITY, oidcSettings } from './oidcSettings'

const origin = window.location.origin

export const userManager = new UserManager({
  ...oidcSettings,
  silent_redirect_uri: `${origin}/oidc/silent.html`,
  post_logout_redirect_uri: `${origin}/login`,
  scope: 'openid profile email',
  // No refresh token is issued to the SPA: the access token is renewed in a hidden iframe while
  // Keycloak's httpOnly session cookie lives, shortly before it expires.
  automaticSilentRenew: true,
  // Tokens stay in memory, never in web storage; only the short-lived sign-in state does.
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
})

userManager.events.addUserLoaded((user) => {
  if (useAuthStore.getState().isAuthenticated) useAuthStore.getState().updateAccessToken(user.access_token)
})

let renewal: Promise<string> | null = null

export const keycloakSession: AuthSession = {
  async restore() {
    try {
      const user = await userManager.signinSilent()
      if (!user) return false
      await establishSession(user.access_token)
      return true
    } catch {
      return false
    }
  },

  signIn: ({ returnTo, register, reauthenticate, action } = {}) =>
    userManager.signinRedirect({
      state: { returnTo: safeReturnTo(returnTo) },
      prompt: register ? 'create' : reauthenticate ? 'login' : undefined,
      extraQueryParams: action ? { kc_action: action } : undefined,
    }),

  async completeSignIn() {
    const user = await userManager.signinRedirectCallback()
    await establishSession(user.access_token)
    return safeReturnTo((user.state as { returnTo?: unknown } | undefined)?.returnTo)
  },

  // Concurrent 401s share one renewal.
  renew() {
    renewal ??= userManager
      .signinSilent()
      .then((user) => {
        if (!user) throw new Error('Signed out')
        useAuthStore.getState().updateAccessToken(user.access_token)
        return user.access_token
      })
      .finally(() => {
        renewal = null
      })
    return renewal
  },

  async signOut() {
    const user = await userManager.getUser()
    useAuthStore.getState().clearAuth()
    // Ends the Keycloak session too, then comes back to /login.
    await userManager.signoutRedirect({ id_token_hint: user?.id_token })
  },

  accountUrl: `${OIDC_AUTHORITY}/account`,
}
