import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import type { AuthSession } from './session'
import { establishSession, safeReturnTo } from './establish'

// `npm run dev:mock` runs without Keycloak: the mock API (mock-plugin.ts) hands out a token for
// the demo account, kept for this tab so a reload stays signed in.
const TOKEN_KEY = 'fate-mock-token'

export const mockSession: AuthSession = {
  async restore() {
    const token = sessionStorage.getItem(TOKEN_KEY)
    if (!token) return false
    try {
      await establishSession(token)
      return true
    } catch {
      sessionStorage.removeItem(TOKEN_KEY)
      return false
    }
  },

  async signIn({ returnTo } = {}) {
    const { data } = await axios.post<{ accessToken: string }>('/api/v1/mock/sign-in')
    sessionStorage.setItem(TOKEN_KEY, data.accessToken)
    window.location.assign(safeReturnTo(returnTo))
  },

  completeSignIn: () => Promise.resolve('/'),

  renew() {
    const token = sessionStorage.getItem(TOKEN_KEY)
    return token ? Promise.resolve(token) : Promise.reject(new Error('Signed out'))
  },

  signOut() {
    sessionStorage.removeItem(TOKEN_KEY)
    useAuthStore.getState().clearAuth()
    return Promise.resolve()
  },

  accountUrl: null,
}
