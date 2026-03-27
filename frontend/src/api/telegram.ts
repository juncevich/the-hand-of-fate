import { apiClient } from './client'

export const telegramApi = {
  getLinkToken: () =>
    apiClient
      .get<{ token: string; expiresAt: string }>('/telegram/link-token')
      .then((r) => r.data),

  unlink: () => apiClient.delete('/telegram/unlink'),
}
