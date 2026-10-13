import { apiClient } from './client'

/** Server-side limit (spring.servlet.multipart.max-file-size); checked early to skip a doomed upload. */
export const MAX_AVATAR_BYTES = 2 * 1024 * 1024
export const AVATAR_ACCEPT = 'image/png,image/jpeg,image/gif'

export const usersApi = {
  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      // The client defaults to JSON; multipart lets axios add the boundary itself
      .put<{ avatarUrl: string }>('/users/me/avatar', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data)
  },

  deleteAvatar: () => apiClient.delete('/users/me/avatar'),
}
