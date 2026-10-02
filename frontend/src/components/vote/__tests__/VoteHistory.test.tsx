import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { votesApi } from '@/api/votes'
import { VoteHistory } from '../VoteHistory'

vi.mock('@/api/votes', () => ({ votesApi: { getHistory: vi.fn() } }))
afterEach(() => vi.clearAllMocks())

it('loads the next history page and permits returning to the first page', async () => {
  vi.mocked(votesApi.getHistory).mockImplementation(async (_, page = 0) => ({
    content: [{ id: `h${page}`, winnerEmail: null, winnerDisplayName: null, winnerOptionTitle: `Winner ${page}`, round: page + 1, drawnAt: '2026-01-01T00:00:00Z' }],
    totalElements: 21, totalPages: 2, number: page, size: 20,
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const { rerender } = render(<QueryClientProvider client={client}><VoteHistory voteId="v1" /></QueryClientProvider>)
  expect(await screen.findByText('Winner 0')).toBeInTheDocument()
  expect(screen.getByText('История (21)')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Назад' })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }))
  expect(await screen.findByText('Winner 1')).toBeInTheDocument()
  expect(votesApi.getHistory).toHaveBeenCalledWith('v1', 1)
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: 'Назад' }))
  expect(await screen.findByText('Winner 0')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }))
  await screen.findByText('Winner 1')
  rerender(<QueryClientProvider client={client}><VoteHistory voteId="v2" /></QueryClientProvider>)
  expect(await screen.findByText('Winner 0')).toBeInTheDocument()
  expect(votesApi.getHistory).toHaveBeenCalledWith('v2', 0)
  client.clear()
})

it('keeps the current page visible while the next page is loading', async () => {
  let resolveNext: (value: Awaited<ReturnType<typeof votesApi.getHistory>>) => void = () => {}
  const pageOf = (page: number) => ({
    content: [{ id: `h${page}`, winnerEmail: null, winnerDisplayName: null, winnerOptionTitle: `Winner ${page}`, round: page + 1, drawnAt: '2026-01-01T00:00:00Z' }],
    totalElements: 21, totalPages: 2, number: page, size: 20,
  })
  vi.mocked(votesApi.getHistory).mockImplementation((_, page = 0) =>
    page === 0 ? Promise.resolve(pageOf(0)) : new Promise((resolve) => { resolveNext = resolve }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><VoteHistory voteId="v1" /></QueryClientProvider>)
  expect(await screen.findByText('Winner 0')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: 'Далее' }))

  // While page 2 is in flight, page 1 stays on screen and pagination is locked.
  expect(screen.getByText('Winner 0')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Назад' })).toBeDisabled()

  resolveNext(pageOf(1))
  expect(await screen.findByText('Winner 1')).toBeInTheDocument()
  expect(screen.queryByText('Winner 0')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Назад' })).toBeEnabled()
  client.clear()
})
