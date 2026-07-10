import { renderHook, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useVoteList } from '../useVoteList'
import { votesApi } from '@/api/votes'

vi.mock('@/api/votes', () => ({
  votesApi: {
    list: vi.fn(),
  },
}))

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

const page = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
}

describe('useVoteList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches the first page by default', async () => {
    vi.mocked(votesApi.list).mockResolvedValueOnce(page)

    const { result } = renderHook(() => useVoteList(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(votesApi.list).toHaveBeenCalledWith(0)
  })

  it('fetches the requested page', async () => {
    vi.mocked(votesApi.list).mockResolvedValueOnce(page)

    const { result } = renderHook(() => useVoteList(2), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(votesApi.list).toHaveBeenCalledWith(2)
  })

  it('exposes an error state when the request fails', async () => {
    vi.mocked(votesApi.list).mockRejectedValueOnce(new Error('network error'))

    const { result } = renderHook(() => useVoteList(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
