import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { useBackendStatus } from '../useBackendStatus'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('useBackendStatus', () => {
  describe('initial fetch', () => {
    it('starts as checking before the first fetch resolves', () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
      const { result } = renderHook(() => useBackendStatus())
      expect(result.current).toBe('checking')
    })

    it('transitions to online when health returns ok', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
      const { result } = renderHook(() => useBackendStatus())

      await act(async () => {})
      expect(result.current).toBe('online')
    })

    it('transitions to offline when health returns non-ok response', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
      const { result } = renderHook(() => useBackendStatus())

      await act(async () => {})
      expect(result.current).toBe('offline')
    })

    it('transitions to offline when fetch throws (network error)', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')))
      const { result } = renderHook(() => useBackendStatus())

      await act(async () => {})
      expect(result.current).toBe('offline')
    })
  })

  describe('polling interval', () => {
    it('polls again after 10 seconds', async () => {
      vi.useFakeTimers()
      const fetchMock = vi.fn().mockResolvedValue({ ok: true })
      vi.stubGlobal('fetch', fetchMock)

      renderHook(() => useBackendStatus())

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

      const { result } = renderHook(() => useBackendStatus())

      await act(() => vi.advanceTimersByTimeAsync(0))
      expect(result.current).toBe('online')

      await act(() => vi.advanceTimersByTimeAsync(10_000))
      expect(result.current).toBe('offline')
    })

    it('stops polling after unmount', async () => {
      vi.useFakeTimers()
      const fetchMock = vi.fn().mockResolvedValue({ ok: true })
      vi.stubGlobal('fetch', fetchMock)

      const { unmount } = renderHook(() => useBackendStatus())

      await act(() => vi.advanceTimersByTimeAsync(0))
      const callsBeforeUnmount = fetchMock.mock.calls.length

      unmount()

      await act(() => vi.advanceTimersByTimeAsync(30_000))
      expect(fetchMock).toHaveBeenCalledTimes(callsBeforeUnmount)
    })
  })
})
