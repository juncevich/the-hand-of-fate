import axios, { type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'

type AuthRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean; _sessionVersion?: number }

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Endpoints that work without a session: a 401 from them is a real answer, not an expired token.
const SESSIONLESS = /^\/notifications\/unsubscribe/

let renewSession: (() => Promise<string>) | null = null

/** How a rejected access token is replaced: by the sign-in session (src/auth/session.ts). */
export function setSessionRenewal(renew: () => Promise<string>) {
  renewSession = renew
}

apiClient.interceptors.request.use((config) => {
  const auth = useAuthStore.getState()
  const authenticatedConfig = config as AuthRequestConfig
  authenticatedConfig._sessionVersion = auth.sessionVersion
  const token = auth.accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// A 401 means the access token stopped working before its renewal: renew once and retry. When
// the sign-in itself has ended, the session is cleared and the app goes to /login.
apiClient.interceptors.response.use(
  (res) => res,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)
    const original = error.config as AuthRequestConfig | undefined
    if (error.response?.status !== 401 || !original || original._retry || SESSIONLESS.test(original.url ?? '')) {
      return Promise.reject(error)
    }
    const version = useAuthStore.getState().sessionVersion
    if (original._sessionVersion !== version || !renewSession) return Promise.reject(error)
    original._retry = true
    let token: string
    try {
      token = await renewSession()
    } catch (renewError) {
      if (useAuthStore.getState().sessionVersion === version) useAuthStore.getState().clearAuth()
      return Promise.reject(renewError)
    }
    // Also what the retry (and every later request) is sent with.
    useAuthStore.getState().updateAccessToken(token)
    return apiClient(original)
  }
)
