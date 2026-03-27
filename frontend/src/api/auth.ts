import axios from 'axios'
import { apiClient } from './client'

export interface AuthPayload {
  accessToken: string
  refreshToken: string
  userId: string
  email: string
  displayName: string
}

export const authApi = {
  register: (data: { email: string; password: string; displayName: string }) =>
    apiClient.post<AuthPayload>('/auth/register', data).then((r) => r.data),

  login: (data: { email: string; password: string }) =>
    apiClient.post<AuthPayload>('/auth/login', data).then((r) => r.data),

  logout: (refreshToken: string) =>
    apiClient.post('/auth/logout', { refreshToken }),

  silentRefresh: () =>
    // Use plain axios — no auth header needed, relies on httpOnly cookie
    axios
      .post<AuthPayload>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .then((r) => r.data),
}
