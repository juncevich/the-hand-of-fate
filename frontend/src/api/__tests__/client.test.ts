import { afterEach, describe, expect, it, vi } from 'vitest'
import axios, { type InternalAxiosRequestConfig } from 'axios'
import { apiClient, refreshSession } from '../client'
import { useAuthStore } from '@/store/authStore'

function unauthorizedError(config: InternalAxiosRequestConfig) {
  const err = new Error('Unauthorized') as Error & {
    config: InternalAxiosRequestConfig
    response: { status: number; data: unknown; statusText: string; headers: Record<string, never>; config: InternalAxiosRequestConfig }
    isAxiosError: boolean
  }
  err.config = config
  err.response = { status: 401, data: {}, statusText: 'Unauthorized', headers: {}, config }
  err.isAxiosError = true
  return err
}

function tick() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function waitUntil(condition: () => boolean, maxTicks = 50) {
  for (let i = 0; i < maxTicks; i += 1) {
    if (condition()) return
    await tick()
  }
  throw new Error('condition was not met in time')
}

/** Mimics a real backend: rejects unless the request carries the current valid token. */
function fakeBackendAdapter(validToken: string) {
  return vi.fn(async (config: InternalAxiosRequestConfig) => {
    if (config.headers?.Authorization === `Bearer ${validToken}`) {
      return { data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config }
    }
    throw unauthorizedError(config)
  })
}

describe('apiClient 401 refresh queue', () => {
  afterEach(() => {
    useAuthStore.getState().clearAuth()
    apiClient.defaults.adapter = undefined
    vi.restoreAllMocks()
  })

  it('makes a single refresh call and resolves all queued requests once it succeeds', async () => {
    let refreshCalls = 0
    let resolveRefresh!: (value: { data: { accessToken: string } }) => void
    const refreshPromise = new Promise<{ data: { accessToken: string } }>((resolve) => {
      resolveRefresh = resolve
    })
    vi.spyOn(axios, 'post').mockImplementation(() => {
      refreshCalls += 1
      return refreshPromise as ReturnType<typeof axios.post>
    })

    apiClient.defaults.adapter = fakeBackendAdapter('new-token')

    // First request trips the 401 and starts the refresh (which we hold pending).
    const p1 = apiClient.get('/votes')
    await waitUntil(() => refreshCalls === 1)

    // Second request arrives while a refresh is already in flight — it must queue,
    // not trigger a second refresh call.
    const p2 = apiClient.get('/votes')
    await tick()

    resolveRefresh({ data: { accessToken: 'new-token' } })

    const [r1, r2] = await Promise.all([p1, p2])

    expect(r1.data).toEqual({ ok: true })
    expect(r2.data).toEqual({ ok: true })
    expect(refreshCalls).toBe(1)
  })

  it('rejects every queued request instead of hanging when the refresh call fails', async () => {
    let refreshCalls = 0
    let rejectRefresh!: (err: Error) => void
    const refreshPromise = new Promise((_, reject) => {
      rejectRefresh = reject
    })
    vi.spyOn(axios, 'post').mockImplementation(() => {
      refreshCalls += 1
      return refreshPromise as ReturnType<typeof axios.post>
    })

    apiClient.defaults.adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      throw unauthorizedError(config)
    })

    const p1 = apiClient.get('/votes')
    await waitUntil(() => refreshCalls === 1)

    const p2 = apiClient.get('/votes')
    await tick()

    rejectRefresh(new Error('refresh failed'))

    const results = await Promise.allSettled([p1, p2])

    expect(results[0].status).toBe('rejected')
    expect(results[1].status).toBe('rejected')
  })
  it('does not refresh when login rejects invalid credentials', async () => {
    const refresh = vi.spyOn(axios, 'post')
    apiClient.defaults.adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      throw unauthorizedError(config)
    })
    await expect(apiClient.post('/auth/login', { email: 'a@test.com', password: 'wrong' })).rejects.toThrow('Unauthorized')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('retries every concurrent request at most once even if the new token is rejected', async () => {
    let complete!: (value: { data: { accessToken: string } }) => void
    const refresh = vi.spyOn(axios, 'post').mockImplementation(() => new Promise((resolve) => { complete = resolve }))
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => { throw unauthorizedError(config) })
    apiClient.defaults.adapter = adapter
    const first = apiClient.get('/votes')
    await waitUntil(() => refresh.mock.calls.length === 1)
    const second = apiClient.get('/votes')
    await tick()
    complete({ data: { accessToken: 'still-invalid' } })
    const results = await Promise.allSettled([first, second])
    expect(results.every((result) => result.status === 'rejected')).toBe(true)
    expect(refresh).toHaveBeenCalledOnce()
    expect(adapter).toHaveBeenCalledTimes(4)
  })

  it('shares initial session restoration with refresh triggered by a 401', async () => {
    let complete!: (value: { data: { accessToken: string } }) => void
    const refresh = vi.spyOn(axios, 'post').mockImplementation(() => new Promise((resolve) => { complete = resolve }))
    apiClient.defaults.adapter = fakeBackendAdapter('new-token')
    const restoring = refreshSession()
    const request = apiClient.get('/votes')
    await tick()
    complete({ data: { accessToken: 'new-token' } })
    await Promise.all([restoring, request])
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('does not restore a session when refresh finishes after logout', async () => {
    let complete!: (value: { data: { accessToken: string } }) => void
    vi.spyOn(axios, 'post').mockImplementation(() => new Promise((resolve) => { complete = resolve }))
    const restoring = refreshSession()
    useAuthStore.getState().clearAuth()
    complete({ data: { accessToken: 'old-user-token' } })
    await expect(restoring).rejects.toThrow('Session changed')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('does not replay an old request under a newly signed-in account', async () => {
    let rejectRequest!: (error: Error) => void
    let sentConfig!: InternalAxiosRequestConfig
    const refresh = vi.spyOn(axios, 'post')
    apiClient.defaults.adapter = vi.fn((config: InternalAxiosRequestConfig) => {
      sentConfig = config
      return new Promise<never>((_, reject) => { rejectRequest = reject })
    })
    const pending = apiClient.post('/votes', { title: 'Old account vote' })
    await waitUntil(() => !!rejectRequest)
    useAuthStore.getState().setAuth({ accessToken: 'new-account', userId: 'bob', email: 'bob@test.com', displayName: 'Bob' })
    rejectRequest(unauthorizedError(sentConfig))
    await expect(pending).rejects.toThrow('Unauthorized')
    expect(refresh).not.toHaveBeenCalled()
    expect(useAuthStore.getState().userId).toBe('bob')
  })

})
