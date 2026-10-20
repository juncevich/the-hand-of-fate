import { apiClient } from './client'
import type { components } from './generated/schema'

export type EmailPreferences = components['schemas']['EmailPreferencesDto']
export type Profile = components['schemas']['ProfileResponse']

/** Server-side limit (spring.servlet.multipart.max-file-size); checked early to skip a doomed upload. */
export const MAX_AVATAR_BYTES = 2 * 1024 * 1024
export const AVATAR_ACCEPT = 'image/png,image/jpeg,image/gif'

export const usersApi = {
  /** The signed-in account; its first call after a Keycloak sign-in also creates it on the backend. */
  getProfile: () => apiClient.get<Profile>('/users/me').then((r) => r.data),

  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      // The client defaults to JSON; multipart lets axios add the boundary itself
      .put<components['schemas']['AvatarResponse']>('/users/me/avatar', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data)
  },

  deleteAvatar: () => apiClient.delete('/users/me/avatar'),

  getEmailPreferences: () =>
    apiClient.get<EmailPreferences>('/users/me/email-preferences').then((r) => r.data),

  updateEmailPreferences: (notificationsEnabled: boolean) =>
    apiClient.put<EmailPreferences>('/users/me/email-preferences', { notificationsEnabled }).then((r) => r.data),

  /** The opt-out link of a notification email; works without a session. */
  unsubscribe: (email: string, signature: string) =>
    apiClient.post('/notifications/unsubscribe', { email, signature }),
}
