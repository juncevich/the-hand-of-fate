import { UserManager } from 'oidc-client-ts'
import { oidcSettings } from '@/auth/oidcSettings'

// Hands the response of a silent renewal (prompt=none) to the app's UserManager in the parent window.
void new UserManager(oidcSettings).signinSilentCallback()
