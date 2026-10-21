import { i18nBuilder } from 'keycloakify/login'
import type { ThemeName } from '../kc.gen'
import { messagesEn } from './messages_en'
import { messagesRu } from './messages_ru'

/** Keycloak's own messages (ru, en, …) plus the theme's few texts of its own. */
const built = i18nBuilder
  .withThemeName<ThemeName>()
  .withCustomTranslations({
    en: {
      ...messagesEn,
      fateTagline: 'A fair draw for your team',
      fateToggleTheme: 'Toggle theme',
      fateRestartLogin: 'Start signing in again',
    },
    ru: {
      ...messagesRu,
      fateTagline: 'Честный жребий для команды',
      fateToggleTheme: 'Сменить тему',
      fateRestartLogin: 'Начать вход заново',
    },
  })
  .build()

export const { useI18n } = built
export type I18n = typeof built.ofTypeI18n
