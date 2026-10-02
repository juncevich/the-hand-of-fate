import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiClient, refreshSession } from '../client'
import { authApi } from '../auth'
import { telegramApi } from '../telegram'
import { votesApi } from '../votes'

vi.mock('../client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
  refreshSession: vi.fn(),
}))

const ok = <T,>(data: T) => Promise.resolve({ data })

describe('API modules', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockImplementation(() => ok('get-data'))
    vi.mocked(apiClient.post).mockImplementation(() => ok('post-data'))
    vi.mocked(apiClient.delete).mockImplementation(() => ok(undefined))
  })

  describe('authApi', () => {
    it('register posts credentials and unwraps the payload', async () => {
      const body = { email: 'a@b.c', password: 'secret', displayName: 'A' }
      await expect(authApi.register(body)).resolves.toBe('post-data')
      expect(apiClient.post).toHaveBeenCalledWith('/auth/register', body)
    })

    it('login posts credentials and unwraps the payload', async () => {
      const body = { email: 'a@b.c', password: 'secret' }
      await expect(authApi.login(body)).resolves.toBe('post-data')
      expect(apiClient.post).toHaveBeenCalledWith('/auth/login', body)
    })

    it('logout posts to the logout endpoint', async () => {
      await authApi.logout()
      expect(apiClient.post).toHaveBeenCalledWith('/auth/logout')
    })

    it('silentRefresh uses the shared session refresh', async () => {
      const payload = { accessToken: 't', userId: 'u', email: 'a@test.com', displayName: 'A' }
      vi.mocked(refreshSession).mockResolvedValueOnce(payload)
      await expect(authApi.silentRefresh()).resolves.toEqual(payload)
      expect(refreshSession).toHaveBeenCalledOnce()
      expect(apiClient.post).not.toHaveBeenCalled()
    })

  })

  describe('telegramApi', () => {
    it('getLinkToken unwraps the token payload', async () => {
      await expect(telegramApi.getLinkToken()).resolves.toBe('get-data')
      expect(apiClient.get).toHaveBeenCalledWith('/telegram/link-token')
    })

    it('unlink deletes the telegram link', async () => {
      await telegramApi.unlink()
      expect(apiClient.delete).toHaveBeenCalledWith('/telegram/unlink')
    })
  })

  describe('votesApi', () => {
    it('list defaults to the first page of 20', async () => {
      await expect(votesApi.list()).resolves.toBe('get-data')
      expect(apiClient.get).toHaveBeenCalledWith('/votes', { params: { page: 0, size: 20 } })
    })

    it('list passes explicit paging', async () => {
      await votesApi.list(3, 5)
      expect(apiClient.get).toHaveBeenCalledWith('/votes', { params: { page: 3, size: 5 } })
    })

    it.each([
      ['get', () => votesApi.get('v1'), '/votes/v1'],
    ])('%s issues GET and unwraps data', async (_, call, url) => {
      await expect(call()).resolves.toBe('get-data')
      expect(apiClient.get).toHaveBeenCalledWith(url)
    })

    it('fetches a history page', async () => {
      await expect(votesApi.getHistory('v1', 2)).resolves.toBe('get-data')
      expect(apiClient.get).toHaveBeenCalledWith('/votes/v1/history/page', { params: { page: 2, size: 20 } })
    })

    it('create posts the request and unwraps the created vote', async () => {
      const req = { title: 'T', mode: 'SIMPLE' as const, participantEmails: [], options: [] }
      await expect(votesApi.create(req)).resolves.toBe('post-data')
      expect(apiClient.post).toHaveBeenCalledWith('/votes', req)
    })

    it('draw posts and unwraps the draw result', async () => {
      await expect(votesApi.draw('v1')).resolves.toBe('post-data')
      expect(apiClient.post).toHaveBeenCalledWith('/votes/v1/draw')
    })

    it.each([
      ['addParticipant', () => votesApi.addParticipant('v1', 'x@y.z'), '/votes/v1/participants', { email: 'x@y.z' }],
      ['addOption', () => votesApi.addOption('v1', 'Pizza'), '/votes/v1/options', { title: 'Pizza' }],
    ])('%s posts the body', async (_, call, url, body) => {
      await call()
      expect(apiClient.post).toHaveBeenCalledWith(url, body)
    })

    it.each([
      ['reopen', () => votesApi.reopen('v1'), '/votes/v1/reopen'],
      ['close', () => votesApi.close('v1'), '/votes/v1/close'],
    ])('%s posts without a body', async (_, call, url) => {
      await call()
      expect(apiClient.post).toHaveBeenCalledWith(url)
    })

    it.each([
      ['delete', () => votesApi.delete('v1'), '/votes/v1'],
      ['removeParticipant', () => votesApi.removeParticipant('v1', 'x@y.z'), '/votes/v1/participants/x@y.z'],
      ['removeOption', () => votesApi.removeOption('v1', 'o1'), '/votes/v1/options/o1'],
    ])('%s issues DELETE', async (_, call, url) => {
      await call()
      expect(apiClient.delete).toHaveBeenCalledWith(url)
    })
  })
})
