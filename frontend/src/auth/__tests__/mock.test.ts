import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { mockSession } from '../mock'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/users', () => ({
  usersApi: { getProfile: vi.fn().mockResolvedValue({ userId: 'u1', email: 'demo@example.com', displayName: 'Demo', avatarUrl: null }) },
}))

describe('mockSession (npm run dev:mock)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useAuthStore.getState().clearAuth()
  })
  afterEach(() => vi.restoreAllMocks())

  it('signs in as the demo account and keeps the token for this tab', async () => {
    vi.spyOn(axios, 'post').mockResolvedValueOnce({ data: { accessToken: 'mock-access.u1' } })
    const assign = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, assign })

    await mockSession.signIn({ returnTo: '/votes/1' })

    expect(assign).toHaveBeenCalledWith('/votes/1')
    await expect(mockSession.restore()).resolves.toBe(true)
    expect(useAuthStore.getState()).toMatchObject({ accessToken: 'mock-access.u1', displayName: 'Demo' })
    await expect(mockSession.renew()).resolves.toBe('mock-access.u1')
  })

  it('has nothing to restore or renew after signing out', async () => {
    sessionStorage.setItem('fate-mock-token', 'mock-access.u1')

    await mockSession.signOut()

    await expect(mockSession.restore()).resolves.toBe(false)
    await expect(mockSession.renew()).rejects.toThrow('Signed out')
    await expect(mockSession.completeSignIn()).resolves.toBe('/')
    expect(mockSession.accountUrl).toBeNull()
  })

  it('forgets a token the mock API no longer accepts', async () => {
    const { usersApi } = await import('@/api/users')
    vi.mocked(usersApi.getProfile).mockRejectedValueOnce(new Error('401'))
    sessionStorage.setItem('fate-mock-token', 'stale')

    await expect(mockSession.restore()).resolves.toBe(false)
    expect(sessionStorage.getItem('fate-mock-token')).toBeNull()
  })
})
