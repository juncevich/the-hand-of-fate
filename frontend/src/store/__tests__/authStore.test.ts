import { beforeEach, describe, expect, it } from 'vitest'
import { queryClient } from '@/lib/queryClient'
import { useAuthStore } from '../authStore'

const alice = { accessToken: 'a', userId: 'alice', email: 'alice@test.com', displayName: 'Alice' }

describe('session cache isolation', () => {
  beforeEach(() => useAuthStore.getState().clearAuth())

  it('clears private data on logout and when another account signs in', () => {
    useAuthStore.getState().setAuth(alice)
    queryClient.setQueryData(['votes', 0, 'alice'], ['private vote'])
    useAuthStore.getState().clearAuth()
    expect(queryClient.getQueryData(['votes', 0, 'alice'])).toBeUndefined()

    useAuthStore.getState().setAuth(alice)
    queryClient.setQueryData(['vote', 'v1', 'alice'], { title: 'private' })
    useAuthStore.getState().setAuth({ ...alice, userId: 'bob' })
    expect(queryClient.getQueryData(['vote', 'v1', 'alice'])).toBeUndefined()
  })

  it('preserves the cache when only the access token changes', () => {
    useAuthStore.getState().setAuth(alice)
    queryClient.setQueryData(['votes', 0, 'alice'], ['vote'])
    useAuthStore.getState().setAuth({ ...alice, accessToken: 'new' })
    expect(queryClient.getQueryData(['votes', 0, 'alice'])).toEqual(['vote'])
  })

  it('cancels an in-flight query so it cannot repopulate the cache after logout', async () => {
    useAuthStore.getState().setAuth(alice)
    let complete!: (value: string[]) => void
    const pending = queryClient.fetchQuery({
      queryKey: ['votes', 0, 'alice'],
      queryFn: () => new Promise<string[]>((resolve) => { complete = resolve }),
    }).catch(() => undefined)
    useAuthStore.getState().clearAuth()
    complete(['private vote'])
    await pending
    expect(queryClient.getQueryData(['votes', 0, 'alice'])).toBeUndefined()
  })
})
