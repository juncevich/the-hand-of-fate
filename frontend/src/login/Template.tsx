import { useEffect } from 'react'
import { Moon, RotateCcw, Sparkles, Sun } from 'lucide-react'
import { kcSanitize } from 'keycloakify/lib/kcSanitize'
import type { TemplateProps } from 'keycloakify/login/TemplateProps'
import { getKcClsx } from 'keycloakify/login/lib/kcClsx'
import { useInitialize } from 'keycloakify/login/Template.useInitialize'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useThemeStore } from '@/store/themeStore'
import type { I18n } from './i18n'
import type { KcContext } from './KcContext'
import './login.css'

const ALERT_STYLES = {
  success: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
  warning: 'border-fate-gold/30 bg-fate-gold/10 text-fate-gold',
  error: 'border-red-500/30 bg-red-500/10 text-red-400',
  info: 'border-fate-border bg-white/5 text-fate-text',
} as const

/**
 * Frame of every Keycloak page, in the look of the app's own signed-out pages (`AuthLayout`): logo,
 * theme and language switches, the page's card with Keycloak's message on top.
 */
export default function Template(props: TemplateProps<KcContext, I18n>) {
  const {
    displayInfo = false,
    displayMessage = true,
    displayRequiredFields = false,
    headerNode,
    socialProvidersNode = null,
    infoNode = null,
    documentTitle,
    kcContext,
    i18n,
    doUseDefaultCss,
    classes,
    children,
  } = props

  const { kcClsx } = getKcClsx({ doUseDefaultCss, classes })
  const { msg, msgStr, currentLanguage, enabledLanguages } = i18n
  const { realm, auth, url, message, isAppInitiatedAction } = kcContext

  // Same origin as the app, so the same stored theme (src/store/themeStore.ts).
  const { theme, toggleTheme } = useThemeStore()
  useEffect(() => {
    document.documentElement.classList.toggle('light', theme === 'light')
  }, [theme])

  useEffect(() => {
    document.title = documentTitle ?? msgStr('loginTitle', realm.displayName || realm.name)
  }, [documentTitle, msgStr, realm.displayName, realm.name])

  const { isReadyToRender } = useInitialize({ kcContext, doUseDefaultCss })
  if (!isReadyToRender) return null

  const showsAttemptedUsername = auth !== undefined && auth.showUsername && !auth.showResetCredentials

  return (
    <div className="kc-page min-h-screen flex items-center justify-center bg-fate-bg px-4 py-12">
      <div className="absolute top-4 right-4 flex items-center gap-1">
        {enabledLanguages.length > 1 && (
          <nav id="kc-locale" aria-label={msgStr('languages')} className="flex items-center gap-1">
            {enabledLanguages.map(({ languageTag, label, href }) => (
              <a
                key={languageTag}
                href={href}
                lang={languageTag}
                aria-current={languageTag === currentLanguage.languageTag ? 'true' : undefined}
                className={cn(
                  'rounded-md px-2 py-1 text-xs font-medium uppercase no-underline transition-colors',
                  languageTag === currentLanguage.languageTag
                    ? 'text-fate-gold'
                    : 'text-fate-muted hover:text-fate-text',
                )}
                title={label}
              >
                {languageTag}
              </a>
            ))}
          </nav>
        )}
        <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label={msgStr('fateToggleTheme')}>
          {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </Button>
      </div>

      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-96 h-96 bg-fate-gold/5 rounded-full blur-3xl" />
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-64 h-64 bg-purple-500/5 rounded-full blur-2xl" />
      </div>

      <div className="relative w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="w-16 h-16 rounded-2xl bg-fate-gold/15 border border-fate-gold/30 flex items-center justify-center mx-auto mb-4 glow-gold">
            <Sparkles className="w-8 h-8 text-fate-gold" />
          </div>
          <p className="text-2xl font-bold text-fate-text">The Hand of Fate</p>
          <p className="text-sm text-fate-muted mt-1">{msg('fateTagline')}</p>
        </div>

        <main className="glass p-6 space-y-4">
          <header className="space-y-1">
            {showsAttemptedUsername ? (
              <div id="kc-username" className="flex items-center justify-between gap-2">
                <span id="kc-attempted-username" className="text-sm font-medium text-fate-text truncate">
                  {auth.attemptedUsername}
                </span>
                <a
                  id="reset-login"
                  href={url.loginRestartFlowUrl}
                  aria-label={msgStr('restartLoginTooltip')}
                  title={msgStr('restartLoginTooltip')}
                  className="text-fate-muted hover:text-fate-text"
                >
                  <RotateCcw className="w-4 h-4" />
                </a>
              </div>
            ) : (
              <h1 id="kc-page-title" className="text-lg font-semibold text-fate-text">
                {headerNode}
              </h1>
            )}
            {displayRequiredFields && (
              <p className="text-xs text-fate-muted">
                <span className="required">*</span> {msg('requiredFields')}
              </p>
            )}
          </header>

          {/* App-initiated actions should not see warnings about the action they asked for. */}
          {displayMessage && message !== undefined && (message.type !== 'warning' || !isAppInitiatedAction) && (
            <div
              role={message.type === 'error' ? 'alert' : 'status'}
              className={cn(`alert-${message.type}`, 'rounded-lg border px-3 py-2 text-sm', ALERT_STYLES[message.type])}
            >
              <span
                className={kcClsx('kcAlertTitleClass')}
                dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
              />
            </div>
          )}

          <div id="kc-content">{children}</div>

          {auth !== undefined && auth.showTryAnotherWayLink && (
            <form id="kc-select-try-another-way-form" action={url.loginAction} method="post">
              <input type="hidden" name="tryAnotherWay" value="on" />
              <button type="submit" id="try-another-way" className="text-sm text-fate-gold hover:underline cursor-pointer">
                {msg('doTryAnotherWay')}
              </button>
            </form>
          )}

          {socialProvidersNode}
        </main>

        {displayInfo && (
          <div id="kc-info" className="mt-6 text-center text-sm text-fate-muted">
            {infoNode}
          </div>
        )}
      </div>
    </div>
  )
}
