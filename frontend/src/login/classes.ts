import type { ClassKey } from 'keycloakify/login'
import { buttonVariants } from '@/components/ui/button'

const input = [
  'flex h-10 w-full rounded-lg border border-fate-border bg-white/5',
  'px-3 py-2 text-sm text-fate-text placeholder:text-fate-muted',
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-fate-gold',
  'disabled:cursor-not-allowed disabled:opacity-50 transition-colors',
  'aria-[invalid=true]:border-red-500/60',
].join(' ')

/**
 * Keycloakify's pages render with these classes instead of Keycloak's PatternFly CSS: the same
 * Tailwind styles as the app's `Input`, `Button` and `Label`.
 */
export const classes = {
  kcFormClass: 'space-y-4',
  kcFormGroupClass: 'space-y-1.5',
  // Also holds the required-field mark that follows the label
  kcLabelWrapperClass: 'kc-label flex items-baseline gap-1 text-fate-gold',
  kcInputWrapperClass: 'space-y-1.5',
  kcLabelClass: 'block text-sm font-medium text-fate-muted',
  kcInputClass: input,
  kcTextareaClass: input,
  kcInputGroup: 'relative',
  kcInputErrorMessageClass: 'block text-xs text-red-400',
  kcInputHelperTextBeforeClass: 'block text-xs text-fate-muted',
  kcInputHelperTextAfterClass: 'block text-xs text-fate-muted',
  kcFormPasswordVisibilityButtonClass:
    'absolute inset-y-0 right-0 flex items-center px-3 text-fate-muted hover:text-fate-text cursor-pointer',
  kcFormPasswordVisibilityIconShow: 'kc-icon kc-icon-eye',
  kcFormPasswordVisibilityIconHide: 'kc-icon kc-icon-eye-off',
  kcFormSettingClass: 'flex items-center justify-between gap-2 text-sm',
  kcFormOptionsClass: 'text-sm',
  kcFormOptionsWrapperClass: 'text-sm',
  kcFormButtonsClass: 'pt-2',
  kcCheckboxInputClass: 'mr-2 accent-fate-gold',
  kcButtonClass: buttonVariants({ variant: null, size: 'default' }),
  kcButtonPrimaryClass: buttonVariants({ variant: 'default', size: null }),
  kcButtonDefaultClass: buttonVariants({ variant: 'outline', size: null }),
  kcButtonSecondaryClass: buttonVariants({ variant: 'outline', size: null }),
  kcButtonBlockClass: 'w-full',
  kcButtonLargeClass: '',
  kcFormSocialAccountSectionClass: 'kc-social space-y-3 pt-2',
  kcFormSocialAccountListClass: 'grid gap-2',
  kcFormSocialAccountListGridClass: 'grid-cols-2',
  kcFormSocialAccountListButtonClass: `${buttonVariants({ variant: 'outline' })} w-full no-underline`,
  kcFormSocialAccountGridItem: '',
  kcFormSocialAccountNameClass: '',
  kcCommonLogoIdP: 'hidden',
  kcAlertTitleClass: '',
  kcSignUpClass: 'text-sm text-fate-muted',
  kcContentWrapperClass: 'space-y-2',
  kcSelectAuthListClass: 'space-y-2',
  kcSelectAuthListItemClass: `${buttonVariants({ variant: 'outline' })} w-full justify-start`,
  kcRecoveryCodesList: 'grid grid-cols-2 gap-1 font-mono text-sm',
} satisfies { [key in ClassKey]?: string }
