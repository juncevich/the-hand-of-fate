import { beforeEach, describe, expect, it } from 'vitest'
import { Suspense } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { LoginThemePageId } from 'keycloakify/bin/shared/constants'
import KcPage from '../KcPage'
import { getKcContextMock } from '../mocks'
import { useThemeStore } from '@/store/themeStore'

function renderPage(pageId: LoginThemePageId) {
  render(
    <Suspense>
      <KcPage kcContext={getKcContextMock({ pageId })} />
    </Suspense>,
  )
}

describe('Keycloak login theme', () => {
  beforeEach(() => {
    useThemeStore.setState({ theme: 'dark' })
    document.documentElement.classList.remove('light')
  })

  it('renders the sign-in form in the app frame, with the field ids the browser tests use', async () => {
    renderPage('login.ftl')

    expect(await screen.findByRole('heading', { name: 'Вход в учетную запись' })).toBeInTheDocument()
    expect(screen.getByText('Честный жребий для команды')).toBeInTheDocument()
    expect(document.getElementById('username')).toBeInstanceOf(HTMLInputElement)
    expect(document.getElementById('password')).toBeInstanceOf(HTMLInputElement)
    expect(screen.getByRole('button', { name: 'Вход' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Регистрация' })).toBeInTheDocument()
    // Translated by the theme: missing from Keycloak's Russian messages
    expect(screen.getByText('Или войдите через')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Яндекс' })).toBeInTheDocument()
  })

  it('offers only the realm languages and switches the app theme', async () => {
    renderPage('login.ftl')

    const languages = await screen.findByRole('navigation', { name: /язык/i })
    expect(languages.querySelectorAll('a')).toHaveLength(2)

    await userEvent.click(screen.getByRole('button', { name: 'Сменить тему' }))
    expect(useThemeStore.getState().theme).toBe('light')
    expect(document.documentElement).toHaveClass('light')
  })

  it('shows Keycloak messages and field errors', async () => {
    render(
      <Suspense>
        <KcPage
          kcContext={getKcContextMock({
            pageId: 'login-update-password.ftl',
            overrides: { message: { type: 'error', summary: 'Пароли не совпадают.' } },
          })}
        />
      </Suspense>,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Пароли не совпадают.')
    expect(document.getElementById('password-new')).toBeInstanceOf(HTMLInputElement)
    expect(document.getElementById('password-confirm')).toBeInstanceOf(HTMLInputElement)
  })

  it('lets an expired page continue or start over', async () => {
    renderPage('login-page-expired.ftl')

    expect(await screen.findByRole('heading', { name: 'Страница устарела' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Продолжить' })).toHaveAttribute('id', 'loginContinueLink')
    expect(screen.getByRole('link', { name: 'Начать вход заново' })).toHaveAttribute('id', 'loginRestartLink')
  })
})
