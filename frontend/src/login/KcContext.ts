import type { ExtendKcContext } from 'keycloakify/login'
import type { KcEnvName, ThemeName } from '../kc.gen'

export type KcContextExtension = {
  themeName: ThemeName
  properties: Record<KcEnvName, string> & {}
}

export type KcContextExtensionPerPage = Record<string, Record<string, unknown>>

/** What Keycloak hands the login theme on each page (`window.kcContext`). */
export type KcContext = ExtendKcContext<KcContextExtension, KcContextExtensionPerPage>
