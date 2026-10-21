import { StrictMode, lazy, Suspense } from 'react'
import ReactDOM from 'react-dom/client'
import './index.css'

// The same build serves two things: the app, and Keycloak's login pages (src/login/), on which
// Keycloak sets window.kcContext. In development `?kcPage=login.ftl` previews a page on a mock.
const App = lazy(() => import('./Root'))
const KcPage = lazy(() => import('./login/KcPage'))
const KcPagePreview = lazy(() => import('./login/Preview'))

const previewPageId = import.meta.env.DEV ? new URLSearchParams(location.search).get('kcPage') : null

ReactDOM.createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Suspense>
      {window.kcContext?.themeType === 'login' ? (
        <KcPage kcContext={window.kcContext} />
      ) : previewPageId ? (
        <KcPagePreview pageId={previewPageId} />
      ) : (
        <App />
      )}
    </Suspense>
  </StrictMode>,
)
