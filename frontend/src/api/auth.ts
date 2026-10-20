import { apiClient } from './client'

// Signing in, registration and passwords live in Keycloak (src/auth/session.ts); these are the
// account operations the backend still owns.
export const authApi = {
  /**
   * Irreversible: deletes the account and the votes it created. Needs a sign-in within the last
   * minutes — otherwise 403 with code REAUTHENTICATION_REQUIRED.
   */
  deleteAccount: () => apiClient.post('/auth/delete-account', {}),

  /** Ends every session of the account, in Keycloak too. */
  logoutAll: () => apiClient.post('/auth/logout-all'),
}
