import { expect, type APIRequestContext } from '@playwright/test'
import { PASSWORD } from './accounts'

/**
 * Signs in through the REST API and returns a client for setup the UI would make slow (e.g. many
 * draws). Use Playwright's `request` fixture: its cookies are separate from the page's session.
 */
export async function apiAs(request: APIRequestContext, email: string, password = PASSWORD) {
  const login = await request.post('/api/v1/auth/login', { data: { email, password } })
  expect(login.status(), 'API login').toBe(200)
  const { accessToken } = (await login.json()) as { accessToken: string }
  const headers = { Authorization: `Bearer ${accessToken}` }

  return {
    async draw(voteId: string): Promise<void> {
      const response = await request.post(`/api/v1/votes/${voteId}/draw`, { headers })
      expect(response.status(), 'draw').toBe(200)
    },
    async reopen(voteId: string): Promise<void> {
      const response = await request.post(`/api/v1/votes/${voteId}/reopen`, { headers })
      expect(response.status(), 'reopen').toBe(204)
    },
  }
}
