import { createGetKcContextMock } from 'keycloakify/login/KcContext'
import type { KcContextExtension, KcContextExtensionPerPage } from './KcContext'
import { themeNames, kcEnvDefaults } from '../kc.gen'

/** Keycloak's pages without Keycloak: dev preview (`?kcPage=`) and tests. Shaped like realm `fate`. */
export const { getKcContextMock } = createGetKcContextMock({
  kcContextExtension: {
    themeName: themeNames[0],
    properties: { ...kcEnvDefaults },
  } satisfies KcContextExtension,
  kcContextExtensionPerPage: {} satisfies KcContextExtensionPerPage,
  overrides: {
    locale: {
      currentLanguageTag: 'ru',
      supported: [
        { languageTag: 'ru', label: 'Русский', url: '?kc_locale=ru' },
        { languageTag: 'en', label: 'English', url: '?kc_locale=en' },
      ],
    },
    realm: {
      name: 'fate',
      displayName: 'The Hand of Fate',
      registrationEmailAsUsername: true,
      internationalizationEnabled: true,
    },
  },
  overridesPerPage: {
    'login.ftl': {
      realm: {
        registrationAllowed: true,
        loginWithEmailAllowed: true,
        resetPasswordAllowed: true,
        rememberMe: false,
        password: true,
      },
      social: {
        providers: [
          { alias: 'google', providerId: 'google', displayName: 'Google', loginUrl: '#', iconClasses: '' },
          { alias: 'github', providerId: 'github', displayName: 'GitHub', loginUrl: '#', iconClasses: '' },
          { alias: 'yandex', providerId: 'oauth2', displayName: 'Яндекс', loginUrl: '#', iconClasses: '' },
        ],
      },
    },
  },
})
