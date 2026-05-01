import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { VoteDetailPage } from '../VoteDetailPage'
import type { VoteDetail } from '@/types/vote'

vi.mock('@/api/votes', () => ({
  votesApi: {
    get: vi.fn(),
    draw: vi.fn(),
    reopen: vi.fn(),
    addParticipant: vi.fn(),
    removeParticipant: vi.fn(),
    delete: vi.fn(),
    getHistory: vi.fn(),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

const VOTE_ID = 'vote-123'

function makeVote(overrides: Partial<VoteDetail> = {}): VoteDetail {
  return {
    id: VOTE_ID,
    title: 'Кто дежурит?',
    description: null,
    mode: 'SIMPLE',
    status: 'PENDING',
    currentRound: 1,
    participants: [
      { email: 'alice@example.com', displayName: 'Alice' },
      { email: 'bob@example.com', displayName: 'Bob' },
    ],
    options: [],
    lastResult: null,
    isCreator: true,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

const createWrapper = (queryClient: QueryClient) =>
  ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/votes/${VOTE_ID}`]}>
        <Routes>
          <Route path="/votes/:id" element={children} />
          <Route path="/" element={<div>Dashboard</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )

describe('VoteDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders vote title, status badge and mode badge', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Кто дежурит?')).toBeInTheDocument()
    expect(screen.getByText('Ожидает')).toBeInTheDocument()
    expect(screen.getByText('Простой')).toBeInTheDocument()
  })

  it('shows the draw button when creator, PENDING and has participants', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByRole('button', { name: /Пусть Рука Судьбы решит/i })).toBeInTheDocument()
  })

  it('hides the draw button when user is not creator', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ isCreator: false }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.queryByRole('button', { name: /Пусть Рука Судьбы решит/i })).not.toBeInTheDocument()
  })

  it('hides the draw button when there are no participants', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ participants: [] }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.queryByRole('button', { name: /Пусть Рука Судьбы решит/i })).not.toBeInTheDocument()
  })

  it('calls votesApi.draw and shows winner toast on draw', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue([])
    vi.mocked(votesApi.draw).mockResolvedValueOnce({
      winnerEmail: 'alice@example.com',
      winnerDisplayName: 'Alice',
      winnerOptionTitle: null,
      round: 1,
      newRoundStarted: false,
    })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    const drawBtn = await screen.findByRole('button', { name: /Пусть Рука Судьбы решит/i })
    await userEvent.click(drawBtn)

    await waitFor(() => {
      expect(votesApi.draw).toHaveBeenCalledWith(VOTE_ID)
      expect(toast).toHaveBeenCalledWith('✦ Рука Судьбы выбрала!', 'Победитель: Alice')
    })
  })

  it('renders participants list', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
  })

  it('shows add-participant input for creator in PENDING status', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.getByPlaceholderText('Добавить участника по email')).toBeInTheDocument()
  })

  it('hides add-participant input when not creator', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ isCreator: false }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.queryByPlaceholderText('Добавить участника по email')).not.toBeInTheDocument()
  })

  it('calls votesApi.addParticipant on Enter in add-participant input', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue([])
    vi.mocked(votesApi.addParticipant).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    const input = await screen.findByPlaceholderText('Добавить участника по email')
    await user.type(input, 'carol@example.com{Enter}')

    await waitFor(() => {
      expect(votesApi.addParticipant).toHaveBeenCalledWith(VOTE_ID, 'carol@example.com')
    })
  })

  it('shows remove-participant buttons for creator in PENDING status', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Alice')
    // 2 participants → 2 remove buttons
    const removeButtons = screen.getAllByRole('button', { name: '' }).filter(
      (btn) => btn.querySelector('svg')
    )
    // At least 2 small remove buttons visible (one per participant)
    expect(removeButtons.length).toBeGreaterThanOrEqual(2)
  })

  it('calls votesApi.removeParticipant when remove button is clicked', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue([])
    vi.mocked(votesApi.removeParticipant).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    // Find the row for Alice and click its remove button
    const aliceRow = (await screen.findByText('alice@example.com')).closest('div.flex')!
    const removeBtn = aliceRow.querySelector('button')!
    await user.click(removeBtn)

    await waitFor(() => {
      expect(votesApi.removeParticipant).toHaveBeenCalledWith(VOTE_ID, 'alice@example.com')
    })
  })

  it('renders history section when history has entries', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([
      {
        id: 'h1',
        winnerEmail: 'alice@example.com',
        winnerDisplayName: 'Alice',
        winnerOptionTitle: null,
        round: 1,
        drawnAt: '2024-01-15T10:00:00Z',
      },
    ])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('История (1)')).toBeInTheDocument()
    expect(screen.getByText('Раунд 1')).toBeInTheDocument()
  })

  it('navigates back to dashboard on back button click', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    await user.click(screen.getByRole('button', { name: /Назад/i }))

    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })

  it('displays last result when vote has one', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(
      makeVote({
        status: 'DRAWN',
        lastResult: {
          id: 'h1',
          winnerEmail: 'alice@example.com',
          winnerDisplayName: 'Alice',
          winnerOptionTitle: null,
          round: 1,
          drawnAt: '2024-01-15T10:00:00Z',
        },
      })
    )
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Последний результат')).toBeInTheDocument()
    expect(screen.getAllByText('Alice').length).toBeGreaterThan(0)
  })
})
