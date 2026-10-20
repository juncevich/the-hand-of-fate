import { expect, test } from '@playwright/test'
import { expectKeycloakPage, signOut } from '../support/accounts'

// The e2e overlay's mock OIDC provider (infra/k8s/overlays/e2e/mock-oidc.yaml) stands in for
// Google & co.: it signs in social@e2e.test, "Social User", with a verified email, without asking.
test('a social sign-in creates the account from the provider and signs in again later', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Войти' }).click()
  await expectKeycloakPage(page)
  await page.getByRole('link', { name: 'E2E Mock' }).click()

  // The provider vouches for the email: no password, no verification email.
  await expect(page).toHaveURL('/')
  await expect(page.getByText('Social User')).toBeVisible()

  // Linked from now on: the next sign-in goes straight through.
  await signOut(page)
  await page.getByRole('button', { name: 'Войти' }).click()
  await page.getByRole('link', { name: 'E2E Mock' }).click()
  await expect(page).toHaveURL('/')
})
