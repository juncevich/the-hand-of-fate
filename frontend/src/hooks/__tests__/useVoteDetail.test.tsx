import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { useVoteDetail } from '../useVoteDetail'
import { votesApi } from '@/api/votes'

vi.mock('@/api/votes', () => ({ votesApi: { get: vi.fn(), addParticipant: vi.fn(), delete: vi.fn() } }))
vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

it('invalidates cached summaries after participant changes and deletion', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const listKey = ['votes', 0, 'alice']
  vi.mocked(votesApi.get).mockResolvedValue({ id: 'v1' } as Awaited<ReturnType<typeof votesApi.get>>)
  vi.mocked(votesApi.addParticipant).mockResolvedValue({} as never)
  vi.mocked(votesApi.delete).mockResolvedValue({} as never)
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}><MemoryRouter>{children}</MemoryRouter></QueryClientProvider>
  )
  const { result } = renderHook(() => useVoteDetail('v1'), { wrapper })
  await waitFor(() => expect(result.current.vote?.id).toBe('v1'))
  client.setQueryData(listKey, ['old summary'])
  await act(() => result.current.addParticipant.mutateAsync('new@test.com'))
  expect(client.getQueryState(listKey)?.isInvalidated).toBe(true)
  client.setQueryData(listKey, ['updated summary'])
  await act(() => result.current.deleteVote.mutateAsync())
  expect(client.getQueryState(listKey)?.isInvalidated).toBe(true)
  client.clear()
})
