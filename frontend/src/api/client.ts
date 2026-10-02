import axios, { type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'
import type { AuthPayload } from './auth'

type AuthRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean; _sessionVersion?: number }

export const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

let refreshPromise: Promise<AuthPayload> | null = null
let refreshVersion: number | null = null

export function refreshSession(): Promise<AuthPayload> {
  const version = useAuthStore.getState().sessionVersion
  if (refreshPromise && refreshVersion === version) return refreshPromise
  refreshVersion = version
  const pending = axios.post<AuthPayload>('/api/v1/auth/refresh', {}, { withCredentials: true })
    .then(({ data }) => {
      if (useAuthStore.getState().sessionVersion !== version) {
        throw new Error('Session changed during refresh')
      }
      useAuthStore.getState().setAuth(data)
      return data
    })
    .finally(() => {
      if (refreshPromise === pending) {
        refreshPromise = null
        refreshVersion = null
      }
    })
  refreshPromise = pending
  return pending
}

apiClient.interceptors.request.use((config) => {
  const auth = useAuthStore.getState()
  const authenticatedConfig = config as AuthRequestConfig
  authenticatedConfig._sessionVersion = auth.sessionVersion
  const token = auth.accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

apiClient.interceptors.response.use(
  (res) => res,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)
    const original = error.config as AuthRequestConfig | undefined
    if (error.response?.status !== 401 || !original || original._retry || original.url?.startsWith('/auth/')) {
      return Promise.reject(error)
    }
    if (original._sessionVersion !== useAuthStore.getState().sessionVersion) return Promise.reject(error)
    original._retry = true
    const version = useAuthStore.getState().sessionVersion
    let data: AuthPayload
    try {
      data = await refreshSession()
    } catch (refreshError) {
      if (useAuthStore.getState().sessionVersion === version) useAuthStore.getState().clearAuth()
      return Promise.reject(refreshError)
    }
    original.headers.Authorization = `Bearer ${data.accessToken}`
    return apiClient(original)
  }
)
