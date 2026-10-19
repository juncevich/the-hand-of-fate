import { expect, test } from '@playwright/test'
import { PASSWORD, expectToast, logIn, registerVerified, uniqueEmail } from '../support/accounts'
import { mailedLink } from '../support/mailpit'

test('a new account signs in only after following the verification email', async ({ page }) => {
  const email = uniqueEmail('signup')
  await page.goto('/register')
  await page.getByLabel('Имя').fill('Ann')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Зарегистрироваться' }).click()
  await expect(page.getByText('Проверьте почту')).toBeVisible()

  // Before verification the right password still doesn't sign in.
  await logIn(page, email)
  await expect(page.getByText('Сначала подтвердите адрес почты')).toBeVisible()

  await page.goto(await mailedLink(email, '/verify-email'))
  await expect(page).toHaveURL('/')
  await expectToast(page, 'Email подтверждён')

  await page.getByRole('button', { name: 'Выйти' }).click()
  await expect(page).toHaveURL('/login')
  await logIn(page, email, 'wrong-password')
  await expectToast(page, 'Неверный email или пароль')
  await logIn(page, email)
  await expect(page).toHaveURL('/')
})

test('a forgotten password is reset through the mailed link', async ({ page }) => {
  const email = uniqueEmail('reset')
  await registerVerified(page, email)
  await page.getByRole('button', { name: 'Выйти' }).click()

  await page.getByRole('link', { name: 'Забыли пароль?' }).click()
  await page.getByLabel('Email').fill(email)
  await page.getByRole('button', { name: 'Отправить ссылку' }).click()
  await expect(page.getByText(/Если аккаунт с адресом/)).toBeVisible()

  await page.goto(await mailedLink(email, '/reset-password'))
  await page.getByLabel('Новый пароль').fill('New-e2e-password-2')
  await page.getByLabel('Повторите пароль').fill('New-e2e-password-2')
  await page.getByRole('button', { name: 'Сохранить пароль' }).click()
  await expect(page).toHaveURL('/login')

  await logIn(page, email, 'New-e2e-password-2')
  await expect(page).toHaveURL('/')
})
