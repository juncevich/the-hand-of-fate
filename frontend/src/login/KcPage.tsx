import { lazy } from 'react'
import DefaultPage from 'keycloakify/login/DefaultPage'
import { classes } from './classes'
import { useI18n } from './i18n'
import type { KcContext } from './KcContext'
import Template from './Template'

const UserProfileFormFields = lazy(() => import('keycloakify/login/UserProfileFormFields'))
const LoginPageExpired = lazy(() => import('./pages/LoginPageExpired'))

/**
 * One page of Keycloak's login flow in the app's look: Keycloakify's page with our Template and
 * classes, or a page of our own (pages/) where its markup doesn't fit.
 */
export default function KcPage({ kcContext }: { kcContext: KcContext }) {
  const { i18n } = useI18n({ kcContext })
  const common = { i18n, classes, Template, doUseDefaultCss: false } as const

  switch (kcContext.pageId) {
    case 'login-page-expired.ftl':
      return <LoginPageExpired kcContext={kcContext} {...common} />
  }

  return (
    <DefaultPage
      kcContext={kcContext}
      i18n={i18n}
      classes={classes}
      Template={Template}
      doUseDefaultCss={false}
      UserProfileFormFields={UserProfileFormFields}
      doMakeUserConfirmPassword
    />
  )
}
