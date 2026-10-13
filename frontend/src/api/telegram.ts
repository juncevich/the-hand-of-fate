import { apiClient } from './client'
import type { components } from './generated/schema'

export const telegramApi = {
  getLinkToken: () =>
    apiClient
      .get<components['schemas']['LinkTokenResponse']>('/telegram/link-token')
      .then((r) => r.data),

  unlink: () => apiClient.delete('/telegram/unlink'),
}
