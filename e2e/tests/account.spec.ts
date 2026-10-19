import { expect, test } from '@playwright/test'
import { PASSWORD, expectToast, logIn, registerVerified, uniqueEmail } from '../support/accounts'
import { mailedLink } from '../support/mailpit'

test('an invited address unsubscribes through the link in the invitation', async ({ page }) => {
  const guest = uniqueEmail('guest')
  await registerVerified(page, uniqueEmail('host'))

  await page.getByRole('button', { name: 'Создать голосование' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/Название/).fill('Кто дежурит')
  await dialog.getByPlaceholder('email@example.com').fill(guest)
  await dialog.getByPlaceholder('email@example.com').press('Enter')
  await dialog.getByRole('button', { name: /^Создать$/ }).click()
  await expectToast(page, 'Голосование создано!')

  // The guest has no account: the signed link alone is enough.
  await page.context().clearCookies()
  await page.goto(await mailedLink(guest, '/unsubscribe'))
  await expect(page.getByText(guest)).toBeVisible()
  await page.getByRole('button', { name: 'Отписаться' }).click()
  await expect(page.getByText('Вы отписались')).toBeVisible()
})

test('a deleted account is gone with its votes', async ({ page }) => {
  const email = uniqueEmail('delete')
  await registerVerified(page, email)
  await page.getByRole('button', { name: 'Создать голосование' }).click()
  await page.getByRole('dialog').getByLabel(/Название/).fill('Временное')
  await page.getByRole('dialog').getByRole('button', { name: /^Создать$/ }).click()
  await expectToast(page, 'Голосование создано!')

  await page.goto('/settings')
  await page.getByRole('button', { name: 'Удалить аккаунт' }).click()
  await expect(page.getByText('Будет удалено голосований, созданных вами: 1.', { exact: false })).toBeVisible()
  await page.getByLabel('Пароль для подтверждения').fill('wrong-password')
  await page.getByRole('button', { name: 'Удалить навсегда' }).click()
  await expectToast(page, 'Неверный пароль')

  await page.getByLabel('Пароль для подтверждения').fill(PASSWORD)
  await page.getByRole('button', { name: 'Удалить навсегда' }).click()
  await expect(page).toHaveURL('/login')

  await logIn(page, email)
  await expectToast(page, 'Неверный email или пароль')
})
