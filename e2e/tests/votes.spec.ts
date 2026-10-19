import { expect, test, type Page } from '@playwright/test'
import { expectToast, registerVerified, uniqueEmail } from '../support/accounts'

const DRAW = /Пусть Рука Судьбы решит/

/** The winner shown under «Последний результат»: display name, else email. */
async function lastWinner(page: Page): Promise<string> {
  const block = page.getByRole('heading', { name: 'Последний результат' }).locator('xpath=..')
  return (await block.locator('p').first().textContent()) ?? ''
}

test('a fair rotation vote lets every participant win once per round', async ({ page }) => {
  const guest = uniqueEmail('guest')
  const extra = uniqueEmail('extra')
  await registerVerified(page, uniqueEmail('fair'), 'Хозяин')

  await page.getByRole('button', { name: 'Создать голосование' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/Название/).fill('Дежурство')
  await dialog.getByRole('button', { name: /^Справедливый/ }).click()
  await dialog.getByPlaceholder('email@example.com').fill(guest)
  await dialog.getByPlaceholder('email@example.com').press('Enter')
  await dialog.getByRole('button', { name: /^Создать$/ }).click()
  await expectToast(page, 'Голосование создано!')

  await page.getByRole('link', { name: /Дежурство/ }).click()
  await expect(page.getByText('Раунд 1')).toBeVisible()
  // The creator takes part too.
  await expect(page.getByText('Участники (2)')).toBeVisible()

  await page.getByPlaceholder('Добавить участника по email').fill(extra)
  await page.getByPlaceholder('Добавить участника по email').press('Enter')
  await expectToast(page, 'Участник добавлен')
  await expect(page.getByText('Участники (3)')).toBeVisible()
  await page.getByRole('button', { name: `Убрать ${extra}` }).click()
  await expectToast(page, 'Участник удалён')
  await expect(page.getByText('Участники (2)')).toBeVisible()

  // Round 1: two draws, two different winners — nobody wins twice before everyone won once.
  const winners: string[] = []
  for (let draw = 1; draw <= 2; draw++) {
    if (draw > 1) await page.getByRole('button', { name: 'Голосовать снова' }).click()
    await page.getByRole('button', { name: DRAW }).click()
    await expect(page.getByText(`История (${draw})`)).toBeVisible()
    winners.push(await lastWinner(page))
  }
  expect(winners.sort()).toEqual(['Хозяин', guest].sort())

  // Everyone has won: the next draw opens round 2.
  await page.getByRole('button', { name: 'Голосовать снова' }).click()
  await page.getByRole('button', { name: DRAW }).click()
  await expect(page.getByText('История (3)')).toBeVisible()
  await expect(page.getByText('Раунд 2').first()).toBeVisible()

  // Deleting asks first; cancelling keeps the vote.
  await page.getByRole('button', { name: 'Удалить голосование' }).click()
  const confirm = page.getByRole('dialog', { name: 'Удалить голосование?' })
  await confirm.getByRole('button', { name: 'Отмена' }).click()
  await expect(confirm).toBeHidden()
  await expect(page.getByText('История (3)')).toBeVisible()
  await page.getByRole('button', { name: 'Удалить голосование' }).click()
  await confirm.getByRole('button', { name: 'Удалить', exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('link', { name: /Дежурство/ })).toHaveCount(0)
})

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
  await page.getByRole('button', { name: DRAW }).click()
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

  await page.getByRole('button', { name: DRAW }).click()
  await expect(page.getByText('Последний результат')).toBeVisible()
  await expect(page.getByText('История (1)')).toBeVisible()
})
