import type { PageProps } from 'keycloakify/login/pages/PageProps'
import { buttonVariants } from '@/components/ui/button'
import type { I18n } from '../i18n'
import type { KcContext } from '../KcContext'

/** The sign-in page was open too long: start over, or carry on where the flow stands. */
export default function LoginPageExpired(props: PageProps<Extract<KcContext, { pageId: 'login-page-expired.ftl' }>, I18n>) {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props
  const { url } = kcContext
  const { msg } = i18n

  return (
    <Template kcContext={kcContext} i18n={i18n} doUseDefaultCss={doUseDefaultCss} classes={classes} headerNode={msg('pageExpiredTitle')}>
      <div className="space-y-2">
        <a id="loginContinueLink" href={url.loginAction} className={`${buttonVariants()} w-full no-underline`}>
          {msg('doContinue')}
        </a>
        <a
          id="loginRestartLink"
          href={url.loginRestartFlowUrl}
          className={`${buttonVariants({ variant: 'outline' })} w-full no-underline`}
        >
          {msg('fateRestartLogin')}
        </a>
      </div>
    </Template>
  )
}
