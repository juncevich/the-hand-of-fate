import { apiClient, refreshSession } from './client'
import type { components } from './generated/schema'

export type AuthPayload = components['schemas']['AuthResponse']
type RegisterRequest = components['schemas']['RegisterRequest']
type LoginRequest = components['schemas']['LoginRequest']
export type RegistrationResponse = components['schemas']['RegistrationResponse']
type ChangePasswordRequest = components['schemas']['ChangePasswordRequest']

export const authApi = {
  /** Starts no session: the account signs in once the mailed link is followed. */
  register: (data: RegisterRequest) =>
    apiClient.post<RegistrationResponse>('/auth/register', data).then((r) => r.data),

  verifyEmail: (token: string) =>
    apiClient.post<AuthPayload>('/auth/verify-email', { token }).then((r) => r.data),

  resendVerification: (email: string) =>
    apiClient.post('/auth/resend-verification', { email }),

  forgotPassword: (email: string) =>
    apiClient.post('/auth/forgot-password', { email }),

  resetPassword: (token: string, newPassword: string) =>
    apiClient.post('/auth/reset-password', { token, newPassword }),

  /** Irreversible: deletes the account and the votes it created. */
  deleteAccount: (password: string) =>
    apiClient.post('/auth/delete-account', { password }),

  changePassword: (data: ChangePasswordRequest) =>
    apiClient.post<AuthPayload>('/auth/change-password', data).then((r) => r.data),

  login: (data: LoginRequest) =>
    apiClient.post<AuthPayload>('/auth/login', data).then((r) => r.data),

  logout: () =>
    apiClient.post('/auth/logout'),

  silentRefresh: refreshSession,
}
