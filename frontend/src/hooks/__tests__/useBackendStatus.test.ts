import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { useBackendStatus } from '../useBackendStatus'

// One fresh client per test (not per render) so no cached status leaks between tests
function renderStatus() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
  return renderHook(() => useBackendStatus(), { wrapper })
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('useBackendStatus', () => {
  describe('initial fetch', () => {
    it('starts as checking before the first fetch resolves', () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
      const { result } = renderStatus()
      expect(result.current).toBe('checking')
    })

    it('transitions to online when health returns ok', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
      const { result } = renderStatus()

      await waitFor(() => expect(result.current).toBe('online'))
    })

    it('transitions to offline when health returns non-ok response', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
      const { result } = renderStatus()

      await waitFor(() => expect(result.current).toBe('offline'))
    })

    it('transitions to offline when fetch throws (network error)', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')))
      const { result } = renderStatus()

      await waitFor(() => expect(result.current).toBe('offline'))
    })
  })

  describe('polling interval', () => {
    it('polls again after 10 seconds', async () => {
      vi.useFakeTimers()
      const fetchMock = vi.fn().mockResolvedValue({ ok: true })
      vi.stubGlobal('fetch', fetchMock)

      renderStatus()

      await act(() => vi.advanceTimersByTimeAsync(0))
      expect(fetchMock).toHaveBeenCalledTimes(1)

      await act(() => vi.advanceTimersByTimeAsync(10_000))
      expect(fetchMock).toHaveBeenCalledTimes(2)
    })

    it('transitions from online to offline when backend goes down', async () => {
      vi.useFakeTimers()
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValue({ ok: false })
      vi.stubGlobal('fetch', fetchMock)

      const { result } = renderStatus()

      await act(() => vi.advanceTimersByTimeAsync(0))
      expect(result.current).toBe('online')

      // +1ms lets React Query's batched notification land after the 10s refetch
      await act(() => vi.advanceTimersByTimeAsync(10_001))
      expect(result.current).toBe('offline')
    })

    it('stops polling after unmount', async () => {
      vi.useFakeTimers()
      const fetchMock = vi.fn().mockResolvedValue({ ok: true })
      vi.stubGlobal('fetch', fetchMock)

      const { unmount } = renderStatus()

      await act(() => vi.advanceTimersByTimeAsync(0))
      const callsBeforeUnmount = fetchMock.mock.calls.length

      unmount()

      await act(() => vi.advanceTimersByTimeAsync(30_000))
      expect(fetchMock).toHaveBeenCalledTimes(callsBeforeUnmount)
    })

    it('treats a health check that hangs for 3 seconds as offline', async () => {
      vi.useFakeTimers()
      // Never resolves on its own; only settles when the request is aborted
      const fetchMock = vi.fn(
        (_url: string, init?: RequestInit) =>
          new Promise((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
          }),
      )
      vi.stubGlobal('fetch', fetchMock)

      const { result } = renderStatus()

      await act(() => vi.advanceTimersByTimeAsync(2_900))
      expect(result.current).toBe('checking')

      await act(() => vi.advanceTimersByTimeAsync(200))
      expect(result.current).toBe('offline')
    })
  })
})
