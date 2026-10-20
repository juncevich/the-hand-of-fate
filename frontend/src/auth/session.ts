import { setSessionRenewal } from '@/api/client'
import { keycloakSession } from './keycloak'
import { mockSession } from './mock'

export { establishSession, safeReturnTo } from './establish'

export interface SignInOptions {
  /** Where to land afterwards: a path of this app. */
  returnTo?: string
  /** Open the registration form instead of the sign-in form. */
  register?: boolean
  /** Ask for the password even if a Keycloak session exists (operations that need a recent sign-in). */
  reauthenticate?: boolean
  /** A Keycloak action to run on the way, e.g. setting a new password. */
  action?: 'UPDATE_PASSWORD'
}

/** Signing in and out: through Keycloak (docs/adr/0001), or an in-memory stand-in for `npm run dev:mock`. */
export interface AuthSession {
  /** Picks up an existing sign-in without user interaction; false when there is none. */
  restore(): Promise<boolean>
  /** Leaves the app for the sign-in page; it comes back to `/oidc/callback`. */
  signIn(options?: SignInOptions): Promise<void>
  /** Finishes the sign-in on `/oidc/callback`; resolves to where to go next. */
  completeSignIn(): Promise<string>
  /** A fresh access token for the current sign-in; rejects when it has ended. */
  renew(): Promise<string>
  signOut(): Promise<void>
  /** Keycloak's own account page (password, linked accounts, sessions); none in mock mode. */
  accountUrl: string | null
}

export const session: AuthSession = import.meta.env.MODE === 'mock' ? mockSession : keycloakSession

// An access token that stops working (expired early, session ended elsewhere) is renewed once.
setSessionRenewal(() => session.renew())
