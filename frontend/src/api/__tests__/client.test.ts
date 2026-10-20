import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import { apiClient, setSessionRenewal } from '../client'
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

const tick = () => new Promise((resolve) => setTimeout(resolve, 0))

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

/**
 * A renewal the test settles itself, shared by concurrent callers as the real one is
 * (src/auth/keycloak.ts); counts how often the client asked for it.
 */
function pendingRenewal() {
  const state = {
    calls: 0,
    resolve: (() => {}) as (token: string) => void,
    reject: (() => {}) as (error: Error) => void,
  }
  const shared = new Promise<string>((resolve, reject) => {
    state.resolve = resolve
    state.reject = reject
  })
  setSessionRenewal(() => {
    state.calls += 1
    return shared
  })
  return state
}

describe('apiClient: a rejected access token', () => {
  beforeEach(() => {
    useAuthStore.getState().setAuth({ accessToken: 'old-token', userId: 'u1', email: 'a@test.com', displayName: 'Ann' })
  })

  afterEach(() => {
    useAuthStore.getState().clearAuth()
    apiClient.defaults.adapter = undefined
    vi.restoreAllMocks()
  })

  it('sends the access token', async () => {
    const adapter = fakeBackendAdapter('old-token')
    apiClient.defaults.adapter = adapter

    await expect(apiClient.get('/votes')).resolves.toMatchObject({ data: { ok: true } })
  })

  it('is renewed, and every concurrent request is retried with the new token', async () => {
    const renewal = pendingRenewal()
    apiClient.defaults.adapter = fakeBackendAdapter('new-token')

    const first = apiClient.get('/votes')
    await waitUntil(() => renewal.calls === 1)
    const second = apiClient.get('/votes')
    await tick()
    renewal.resolve('new-token')

    const results = await Promise.all([first, second])
    expect(results.map((r) => r.data)).toEqual([{ ok: true }, { ok: true }])
  })

  it('ends the session when it cannot be renewed', async () => {
    const renewal = pendingRenewal()
    apiClient.defaults.adapter = fakeBackendAdapter('never')

    const request = apiClient.get('/votes')
    await waitUntil(() => renewal.calls === 1)
    renewal.reject(new Error('Signed out'))

    await expect(request).rejects.toThrow('Signed out')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('is retried at most once, even if the renewed token is rejected too', async () => {
    const renewal = pendingRenewal()
    const adapter = fakeBackendAdapter('never')
    apiClient.defaults.adapter = adapter

    const request = apiClient.get('/votes')
    await waitUntil(() => renewal.calls === 1)
    renewal.resolve('still-invalid')

    await expect(request).rejects.toThrow('Unauthorized')
    expect(adapter).toHaveBeenCalledTimes(2)
  })

  it('is a real answer from the unsubscribe endpoints, which need no session', async () => {
    const renewal = pendingRenewal()
    apiClient.defaults.adapter = fakeBackendAdapter('never')

    await expect(apiClient.post('/notifications/unsubscribe', {})).rejects.toThrow('Unauthorized')
    expect(renewal.calls).toBe(0)
  })

  it('is not retried under an account that signed in meanwhile', async () => {
    const renewal = pendingRenewal()
    let rejectRequest!: (error: Error) => void
    let sentConfig!: InternalAxiosRequestConfig
    apiClient.defaults.adapter = vi.fn((config: InternalAxiosRequestConfig) => {
      sentConfig = config
      return new Promise<never>((_, reject) => { rejectRequest = reject })
    })

    const pending = apiClient.post('/votes', { title: 'Old account vote' })
    await waitUntil(() => !!rejectRequest)
    useAuthStore.getState().setAuth({ accessToken: 'new-account', userId: 'bob', email: 'bob@test.com', displayName: 'Bob' })
    rejectRequest(unauthorizedError(sentConfig))

    await expect(pending).rejects.toThrow('Unauthorized')
    expect(renewal.calls).toBe(0)
    expect(useAuthStore.getState().userId).toBe('bob')
  })
})
