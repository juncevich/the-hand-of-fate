import { afterEach, describe, expect, it, vi } from 'vitest'
import axios, { type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../client'
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
})
