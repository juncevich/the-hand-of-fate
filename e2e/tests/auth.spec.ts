import { expect, test } from '@playwright/test'
import {
  ACTION_TOKEN_PATH,
  PASSWORD,
  expectKeycloakPage,
  expectLoginRejected,
  logIn,
  registerVerified,
  setNewPassword,
  signOut,
  uniqueEmail,
} from '../support/accounts'
import { mailedLink } from '../support/mailpit'

test('a new account sets its password after the verification email and signs in with it', async ({ page }) => {
  const email = uniqueEmail('signup')
  await page.goto('/login')
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await expectKeycloakPage(page)
  // With email verification on, Keycloak asks for no password yet: nobody can claim an address
  // they don't receive mail at.
  await expect(page.locator('#password')).toHaveCount(0)
  await page.locator('#email').fill(email)
  await page.locator('#firstName').fill('Ann')
  await page.locator('#lastName').fill('Lee')
  await page.locator('#lastName').press('Enter')
  await expect(page.getByText('Подтверждение адреса E-mail')).toBeVisible()

  await page.goto(await mailedLink(email, ACTION_TOKEN_PATH))
  await setNewPassword(page, PASSWORD)
  await expect(page).toHaveURL('/')
  await expect(page.getByText('Ann Lee')).toBeVisible()

  await signOut(page)
  await logIn(page, email, 'wrong-password')
  await expectLoginRejected(page)
  await logIn(page, email)
  await expect(page).toHaveURL('/')
})

test('a signed-in page survives a reload and comes back after signing in', async ({ page }) => {
  const email = uniqueEmail('session')
  await registerVerified(page, email)
  await page.goto('/settings')

  // The session is restored silently from Keycloak's cookie (prompt=none in a hidden iframe).
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Настройки' })).toBeVisible()

  await signOut(page)
  // Signed out of Keycloak too: a protected page asks to sign in again and returns there.
  await page.goto('/settings')
  await expect(page).toHaveURL('/login')
  await page.getByRole('button', { name: 'Войти' }).click()
  await expectKeycloakPage(page)
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#password').press('Enter')
  await expect(page).toHaveURL('/settings')
})

test('a forgotten password is reset through the mailed link', async ({ page }) => {
  const email = uniqueEmail('reset')
  await registerVerified(page, email)
  await signOut(page)

  await page.getByRole('button', { name: 'Войти' }).click()
  await page.getByRole('link', { name: 'Забыли пароль?' }).click()
  await page.locator('#username').fill(email)
  await page.locator('#username').press('Enter')
  await expect(page.getByText(/В ближайшее время Вы должны получить письмо/)).toBeVisible()

  // The newest email with an action link is the reset one.
  await page.goto(await mailedLink(email, ACTION_TOKEN_PATH))
  await setNewPassword(page, 'New-e2e-password-2')
  await expect(page).toHaveURL('/')

  await signOut(page)
  await logIn(page, email)
  await expectLoginRejected(page)
  await logIn(page, email, 'New-e2e-password-2')
  await expect(page).toHaveURL('/')
})
