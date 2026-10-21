import type { LoginThemePageId } from 'keycloakify/bin/shared/constants'
import KcPage from './KcPage'
import { getKcContextMock } from './mocks'

/** Dev only (`npm run dev`, `/?kcPage=login.ftl`): a Keycloak page on a mock context. */
export default function Preview({ pageId }: { pageId: string }) {
  return <KcPage kcContext={getKcContextMock({ pageId: pageId as LoginThemePageId })} />
}
