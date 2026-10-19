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

test('a changed password replaces the old one', async ({ page }) => {
  const email = uniqueEmail('change')
  await registerVerified(page, email)
  await page.goto('/settings')

  await page.getByLabel('Текущий пароль').fill('Not-my-password-0')
  await page.getByLabel('Новый пароль').fill('Changed-e2e-password-3')
  await page.getByRole('button', { name: 'Сменить пароль' }).click()
  await expectToast(page, 'Неверный пароль')

  await page.getByLabel('Текущий пароль').fill(PASSWORD)
  await page.getByRole('button', { name: 'Сменить пароль' }).click()
  await expectToast(page, 'Пароль изменён')

  await page.getByRole('button', { name: 'Выйти' }).click()
  await logIn(page, email)
  await expectToast(page, 'Неверный email или пароль')
  await logIn(page, email, 'Changed-e2e-password-3')
  await expect(page).toHaveURL('/')
})

test('vote emails are switched off and on in settings', async ({ page }) => {
  await registerVerified(page, uniqueEmail('prefs'))
  await page.goto('/settings')
  const toggle = page.getByRole('checkbox', { name: /^Приглашения и результаты голосований/ })

  await expect(toggle).toBeChecked()
  await toggle.uncheck()
  await page.reload()
  await expect(toggle).not.toBeChecked()
  await toggle.check()
  await page.reload()
  await expect(toggle).toBeChecked()
})

// A 1×1 PNG: the backend crops and re-encodes every avatar to 256×256.
const PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
)

test('an avatar is uploaded to object storage and removed', async ({ page }) => {
  await registerVerified(page, uniqueEmail('avatar'), 'Аватарка')
  await page.goto('/settings')

  await page.getByLabel('Файл аватара').setInputFiles({ name: 'me.png', mimeType: 'image/png', buffer: PIXEL_PNG })
  await expectToast(page, 'Аватар обновлён')
  // Settings and the top bar both show it; the image really comes back from storage, re-encoded.
  const avatar = page.getByRole('img', { name: 'Аватар Аватарка' }).first()
  await expect(avatar).toBeVisible()
  await expect.poll(() => avatar.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(256)

  await page.getByRole('button', { name: 'Удалить', exact: true }).click()
  await expect(page.getByRole('img', { name: 'Аватар Аватарка' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Загрузить аватар' })).toBeVisible()
})
