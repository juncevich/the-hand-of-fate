import { expect, type APIRequestContext } from '@playwright/test'
import { PASSWORD } from './accounts'

/** Asks Keycloak for a token through `fate-test`, the dev/e2e realm's direct-grant client. */
export async function keycloakToken(request: APIRequestContext, email: string, password = PASSWORD) {
  return request.post('/auth/realms/fate/protocol/openid-connect/token', {
    form: { grant_type: 'password', client_id: 'fate-test', username: email, password },
  })
}

/**
 * A REST client for setup the UI would make slow (e.g. many draws), signed in through Keycloak.
 * Use Playwright's `request` fixture: its cookies are separate from the page's session.
 */
export async function apiAs(request: APIRequestContext, email: string, password = PASSWORD) {
  const token = await keycloakToken(request, email, password)
  expect(token.status(), 'Keycloak sign-in').toBe(200)
  const { access_token: accessToken } = (await token.json()) as { access_token: string }
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
