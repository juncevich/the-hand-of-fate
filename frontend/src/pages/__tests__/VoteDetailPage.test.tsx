import { render, screen, waitFor } from '@testing-library/react'
import { AxiosError, AxiosHeaders } from 'axios'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { VoteDetailPage } from '../VoteDetailPage'
import type { VoteDetail } from '@/types/vote'

vi.mock('@/api/votes', () => ({
  votesApi: {
    get: vi.fn(),
    draw: vi.fn(),
    reopen: vi.fn(),
    addParticipant: vi.fn(),
    removeParticipant: vi.fn(),
    addOption: vi.fn(),
    removeOption: vi.fn(),
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
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Кто дежурит?')).toBeInTheDocument()
    expect(screen.getByText('Ожидает')).toBeInTheDocument()
    expect(screen.getByText('Простой')).toBeInTheDocument()
  })

  it('shows the draw button when creator, PENDING and has participants', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByRole('button', { name: /Пусть Рука Судьбы решит/i })).toBeInTheDocument()
  })

  it('hides the draw button when user is not creator', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ isCreator: false }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.queryByRole('button', { name: /Пусть Рука Судьбы решит/i })).not.toBeInTheDocument()
  })

  it('hides the draw button when there are no participants', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ participants: [] }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.queryByRole('button', { name: /Пусть Рука Судьбы решит/i })).not.toBeInTheDocument()
  })

  it('calls votesApi.draw and shows winner toast on draw', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
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
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
  })

  it('shows add-participant input for creator in PENDING status', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.getByPlaceholderText('Добавить участника по email')).toBeInTheDocument()
  })

  it('hides add-participant input when not creator', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ isCreator: false }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await screen.findByText('Кто дежурит?')
    expect(screen.queryByPlaceholderText('Добавить участника по email')).not.toBeInTheDocument()
  })

  it('calls votesApi.addParticipant on Enter in add-participant input', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
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
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

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
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
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
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [
      {
        id: 'h1',
        winnerEmail: 'alice@example.com',
        winnerDisplayName: 'Alice',
        winnerOptionTitle: null,
        round: 1,
        drawnAt: '2024-01-15T10:00:00Z',
      },
    ], totalElements: 1, totalPages: 1, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('История (1)')).toBeInTheDocument()
    expect(screen.getByText('Раунд 1')).toBeInTheDocument()
  })

  it('navigates back to dashboard on back button click', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

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
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Последний результат')).toBeInTheDocument()
    expect(screen.getAllByText('Alice').length).toBeGreaterThan(0)
  })

  it('shows an error state and refetches on retry', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockRejectedValueOnce(new Error('Сервер недоступен')).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Сервер недоступен')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Повторить' }))

    expect(await screen.findByText('Кто дежурит?')).toBeInTheDocument()
    expect(votesApi.get).toHaveBeenCalledTimes(2)
  })

  it('reopens a drawn vote', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote({ status: 'DRAWN' }))
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
    vi.mocked(votesApi.reopen).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(screen.queryByRole('button', { name: /Пусть Рука Судьбы решит/i })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: /Голосовать снова/i }))

    await waitFor(() => expect(votesApi.reopen).toHaveBeenCalledWith(VOTE_ID))
  })

  it('deletes the vote and navigates to the dashboard', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
    vi.mocked(votesApi.delete).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    const heading = await screen.findByRole('heading', { name: 'Кто дежурит?' })
    await user.click(heading.parentElement!.nextElementSibling as HTMLElement)

    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
    expect(votesApi.delete).toHaveBeenCalledWith(VOTE_ID)
  })

  it('shows the backend error in a toast when a mutation fails', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
    vi.mocked(votesApi.draw).mockRejectedValueOnce(
      new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
        data: { title: 'Cannot draw now' },
        status: 409,
        statusText: 'Conflict',
        headers: {},
        config: { headers: new AxiosHeaders() },
      })
    )

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await user.click(await screen.findByRole('button', { name: /Пусть Рука Судьбы решит/i }))

    await waitFor(() => expect(toast).toHaveBeenCalledWith('Ошибка', 'Cannot draw now', 'error'))
  })

  it('renders options and shows the empty-options hint otherwise', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Варианты (0)')).toBeInTheDocument()
    expect(screen.getByText(/Нет вариантов/)).toBeInTheDocument()
  })

  it('adds an option', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote())
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
    vi.mocked(votesApi.addOption).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    await user.type(await screen.findByPlaceholderText('Добавить вариант'), 'Пицца{Enter}')

    await waitFor(() => {
      expect(votesApi.addOption).toHaveBeenCalledWith(VOTE_ID, 'Пицца')
      expect(toast).toHaveBeenCalledWith('Вариант добавлен')
    })
  })

  it('removes an option', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.get).mockResolvedValue(makeVote({ options: [{ id: 'opt-1', title: 'Пицца' }] }))
    vi.mocked(votesApi.getHistory).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
    vi.mocked(votesApi.removeOption).mockResolvedValueOnce({} as never)

    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    const chip = await screen.findByText('Пицца')
    await user.click(chip.querySelector('button')!)

    await waitFor(() => {
      expect(votesApi.removeOption).toHaveBeenCalledWith(VOTE_ID, 'opt-1')
      expect(toast).toHaveBeenCalledWith('Вариант удалён')
    })
  })

  it('hides option controls when the vote is not editable', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(
      makeVote({ status: 'CLOSED', options: [{ id: 'opt-1', title: 'Пицца' }] })
    )
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    const chip = await screen.findByText('Пицца')
    expect(chip.querySelector('button')).toBeNull()
    expect(screen.queryByPlaceholderText('Добавить вариант')).not.toBeInTheDocument()
    expect(screen.getByText('Закрыт')).toBeInTheDocument()
  })

  it('shows round badge for fair rotation votes', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.get).mockResolvedValueOnce(makeVote({ mode: 'FAIR_ROTATION', currentRound: 3, description: 'Каждую неделю' }))
    vi.mocked(votesApi.getHistory).mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<VoteDetailPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Справедливый')).toBeInTheDocument()
    expect(screen.getByText('Раунд 3')).toBeInTheDocument()
    expect(screen.getByText('Каждую неделю')).toBeInTheDocument()
  })
})
