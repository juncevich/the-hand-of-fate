import { expect, type Page } from '@playwright/test'
import { mailedLink } from './mailpit'

export const PASSWORD = 'E2e-password-1'

/** Where the links Keycloak mails (email verification, password reset) point. */
export const ACTION_TOKEN_PATH = '/auth/realms/fate/login-actions/action-token'

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@e2e.test`
}

/** Keycloak's pages (docs/adr/0001) are served on the app's origin under /auth. */
export async function expectKeycloakPage(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/auth\/realms\/fate\//)
}

/** Fills in Keycloak's "new password" form (after a mailed link, or kc_action=UPDATE_PASSWORD). */
export async function setNewPassword(page: Page, password: string): Promise<void> {
  await page.locator('#password-new').fill(password)
  await page.locator('#password-confirm').fill(password)
  await page.locator('#password-confirm').press('Enter')
}

/**
 * Registers on Keycloak's form, follows the verification email and sets the password — with email
 * verification on, Keycloak asks for it only then. Ends signed in on the dashboard; the app shows
 * the user as "firstName lastName".
 */
export async function registerVerified(page: Page, email: string, firstName = 'E2E', lastName = 'User'): Promise<void> {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await expectKeycloakPage(page)
  await page.locator('#email').fill(email)
  await page.locator('#firstName').fill(firstName)
  await page.locator('#lastName').fill(lastName)
  await page.locator('#lastName').press('Enter')
  await expect(page.getByText('Подтверждение адреса E-mail')).toBeVisible()

  await page.goto(await mailedLink(email, ACTION_TOKEN_PATH))
  await setNewPassword(page, PASSWORD)
  await expect(page).toHaveURL('/')
}

/** Signs in on Keycloak's login page; where it ends depends on the password. */
export async function logIn(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Войти' }).click()
  await expectKeycloakPage(page)
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#password').press('Enter')
}

/** Keycloak rejected the credentials and stays on its login page. */
export async function expectLoginRejected(page: Page): Promise<void> {
  await expectKeycloakPage(page)
  await expect(page.getByText('Неправильное имя пользователя или пароль.')).toBeVisible()
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Выйти', exact: true }).click()
  await expect(page).toHaveURL('/login')
}

/**
 * A toast with [text]. Radix renders it twice (visible + an aria-live copy), so look inside the region.
 * includeHidden: an open modal dialog marks everything outside it aria-hidden, the toasts included.
 */
export async function expectToast(page: Page, text: string): Promise<void> {
  const region = page.getByRole('region', { name: /Notifications/, includeHidden: true })
  await expect(region.getByText(text, { exact: true }).first()).toBeVisible()
}
