import { apiClient, refreshSession } from './client'
import type { components } from './generated/schema'

export type AuthPayload = components['schemas']['AuthResponse']
type RegisterRequest = components['schemas']['RegisterRequest']
type LoginRequest = components['schemas']['LoginRequest']

export const authApi = {
  register: (data: RegisterRequest) =>
    apiClient.post<AuthPayload>('/auth/register', data).then((r) => r.data),

  login: (data: LoginRequest) =>
    apiClient.post<AuthPayload>('/auth/login', data).then((r) => r.data),

  logout: () =>
    apiClient.post('/auth/logout'),

  silentRefresh: refreshSession,
}
