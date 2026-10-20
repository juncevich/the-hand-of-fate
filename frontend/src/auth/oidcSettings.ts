const origin = window.location.origin

/** Realm `fate`, served on this origin under /auth, so its session cookie is first-party. */
export const OIDC_AUTHORITY = `${origin}/auth/realms/fate`

/** The SPA's client; shared by the app and the silent-renew page (oidc/silent.html). */
export const oidcSettings = {
  authority: OIDC_AUTHORITY,
  client_id: 'fate-spa',
  redirect_uri: `${origin}/oidc/callback`,
}
