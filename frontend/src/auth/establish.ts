import { useAuthStore } from '@/store/authStore'
import { usersApi } from '@/api/users'

/** Starts the app's session for [accessToken]: the profile comes from the backend. */
export async function establishSession(accessToken: string) {
  const store = useAuthStore.getState()
  store.updateAccessToken(accessToken)
  const profile = await usersApi.getProfile()
  store.setAuth({ accessToken, ...profile })
}

/** [path] if it is a path of this app, else the root — never another site. */
export function safeReturnTo(path: unknown): string {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : '/'
}
