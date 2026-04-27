import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPage } from '../DashboardPage'
import { votesApi } from '@/api/votes'

vi.mock('@/api/votes', () => ({
  votesApi: {
    list: vi.fn(),
    create: vi.fn(),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

const createWrapper = (queryClient: QueryClient) =>
  ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading skeletons while data is loading', () => {
    vi.mocked(votesApi.list).mockReturnValue(new Promise(() => {}))

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { container } = render(<DashboardPage />, { wrapper: createWrapper(queryClient) })

    const skeletons = container.querySelectorAll('.animate-pulse')
    expect(skeletons.length).toBe(6)
  })

  it('shows empty state when there are no votes', async () => {
    vi.mocked(votesApi.list).mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(<DashboardPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Ещё нет голосований')).toBeInTheDocument()
  })

  it('renders vote cards when votes are returned', async () => {
    vi.mocked(votesApi.list).mockResolvedValueOnce({
      content: [
        {
          id: '1',
          title: 'Кто дежурит?',
          mode: 'SIMPLE',
          status: 'PENDING',
          currentRound: 1,
          participantCount: 3,
          isCreator: true,
          createdAt: new Date().toISOString(),
        },
        {
          id: '2',
          title: 'Второе голосование',
          mode: 'FAIR_ROTATION',
          status: 'DRAWN',
          currentRound: 2,
          participantCount: 5,
          isCreator: false,
          createdAt: new Date().toISOString(),
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(<DashboardPage />, { wrapper: createWrapper(queryClient) })

    expect(await screen.findByText('Кто дежурит?')).toBeInTheDocument()
    expect(screen.getByText('Второе голосование')).toBeInTheDocument()
  })

  it('renders the create vote button', () => {
    vi.mocked(votesApi.list).mockReturnValue(new Promise(() => {}))

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(<DashboardPage />, { wrapper: createWrapper(queryClient) })

    expect(screen.getByRole('button', { name: /Создать голосование/i })).toBeInTheDocument()
  })
})
