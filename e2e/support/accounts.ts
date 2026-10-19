import { expect, type Page } from '@playwright/test'
import { mailedLink } from './mailpit'

export const PASSWORD = 'E2e-password-1'

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@e2e.test`
}

/** Registers through the UI and follows the verification email; ends signed in on the dashboard. */
export async function registerVerified(page: Page, email: string, name = 'E2E User'): Promise<void> {
  await page.goto('/register')
  await page.getByLabel('Имя').fill(name)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Зарегистрироваться' }).click()
  await expect(page.getByText('Проверьте почту')).toBeVisible()

  await page.goto(await mailedLink(email, '/verify-email'))
  await expect(page).toHaveURL('/')
}

export async function logIn(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Войти' }).click()
}

/**
 * A toast with [text]. Radix renders it twice (visible + an aria-live copy), so look inside the region.
 * includeHidden: an open modal dialog marks everything outside it aria-hidden, the toasts included.
 */
export async function expectToast(page: Page, text: string): Promise<void> {
  const region = page.getByRole('region', { name: /Notifications/, includeHidden: true })
  await expect(region.getByText(text, { exact: true }).first()).toBeVisible()
}
