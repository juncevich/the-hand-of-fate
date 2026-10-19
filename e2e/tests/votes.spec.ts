import { expect, test } from '@playwright/test'
import { expectToast, registerVerified, uniqueEmail } from '../support/accounts'

test('an options vote is created, reordered and drawn, with translated errors on the way', async ({ page }) => {
  await registerVerified(page, uniqueEmail('votes'))

  await page.getByRole('button', { name: 'Создать голосование' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/Название/).fill('Обед в пятницу')
  await dialog.getByRole('button', { name: /^Вариант/ }).click()
  await dialog.getByRole('button', { name: /^Создать$/ }).click()
  await expectToast(page, 'Голосование создано!')

  await page.getByRole('link', { name: /Обед в пятницу/ }).click()
  await expect(page.getByText('Из вариантов')).toBeVisible()

  // Drawing without options: the backend's NO_OPTIONS code is shown in Russian.
  await page.getByRole('button', { name: /Пусть Рука Судьбы решит/ }).click()
  await expectToast(page, 'Добавьте хотя бы один вариант')

  for (const option of ['Пицца', 'Суши', 'Бургер']) {
    await page.getByPlaceholder('Добавить вариант').fill(option)
    await page.getByPlaceholder('Добавить вариант').press('Enter')
    await expectToast(page, 'Вариант добавлен')
  }
  await expect(page.getByText('Варианты (3)')).toBeVisible()
  await page.getByRole('button', { name: 'Переместить «Бургер» раньше' }).click()
  await expect(page.getByRole('button', { name: 'Переместить «Бургер» раньше' })).toBeEnabled()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Переместить «Суши» позже' })).toBeDisabled()

  await page.getByRole('button', { name: /Пусть Рука Судьбы решит/ }).click()
  await expect(page.getByText('Последний результат')).toBeVisible()
  await expect(page.getByText('История (1)')).toBeVisible()
})
