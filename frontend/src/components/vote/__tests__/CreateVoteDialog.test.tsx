import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { CreateVoteDialog } from '../CreateVoteDialog'

vi.mock('@/api/votes', () => ({
  votesApi: {
    list: vi.fn(),
    create: vi.fn(),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

async function openDialog() {
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: /Создать голосование/i }))
  return user
}

describe('CreateVoteDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens the dialog when the trigger button is clicked', async () => {
    render(<CreateVoteDialog />, { wrapper: createWrapper() })

    await openDialog()

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText(/Название/i)).toBeInTheDocument()
  })

  it('"Создать" button is disabled when title is empty', async () => {
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await openDialog()

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button', { name: /^Создать$/i })).toBeDisabled()
  })

  it('"Создать" button is enabled when title is filled', async () => {
    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByLabelText(/Название/i), 'Мой опрос')

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button', { name: /^Создать$/i })).not.toBeDisabled()
  })

  it('adds an email to the list on button click', async () => {
    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByPlaceholderText('email@example.com'), 'ivan@example.com')
    await user.click(screen.getByRole('button', { name: 'Добавить участника' }))

    expect(screen.getByText('ivan@example.com')).toBeInTheDocument()
  })

  it('adds an email to the list on Enter key', async () => {
    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByPlaceholderText('email@example.com'), 'ivan@example.com{Enter}')

    expect(screen.getByText('ivan@example.com')).toBeInTheDocument()
  })

  it('rejects invalid email format and shows error toast', async () => {
    const { toast } = await import('@/components/ui/toaster')
    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByPlaceholderText('email@example.com'), 'notanemail{Enter}')

    expect(toast).toHaveBeenCalledWith('Неверный email', undefined, 'error')
    expect(screen.queryByText('notanemail')).not.toBeInTheDocument()
  })

  it('prevents adding duplicate emails', async () => {
    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByPlaceholderText('email@example.com'), 'ivan@example.com{Enter}')
    await user.type(screen.getByPlaceholderText('email@example.com'), 'ivan@example.com{Enter}')

    expect(screen.getAllByText('ivan@example.com')).toHaveLength(1)
  })

  it('removes an email tag when X is clicked', async () => {
    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByPlaceholderText('email@example.com'), 'ivan@example.com{Enter}')
    expect(screen.getByText('ivan@example.com')).toBeInTheDocument()

    const removeBtn = screen.getByText('ivan@example.com').closest('span')!.querySelector('button')!
    await user.click(removeBtn)

    expect(screen.queryByText('ivan@example.com')).not.toBeInTheDocument()
  })

  it('calls votesApi.create with correct payload on submit', async () => {
    const { votesApi } = await import('@/api/votes')
    vi.mocked(votesApi.create).mockResolvedValueOnce({
      id: '1',
      title: 'Мой опрос',
      mode: 'SIMPLE',
      status: 'PENDING',
      currentRound: 1,
      isCreator: true,
      createdAt: new Date().toISOString(),
      description: null,
      participants: [],
      options: [],
      lastResult: null,
    })

    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByLabelText(/Название/i), 'Мой опрос')
    await user.type(screen.getByPlaceholderText('email@example.com'), 'ivan@example.com{Enter}')

    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^Создать$/i }))

    await waitFor(() => {
      expect(votesApi.create).toHaveBeenCalledWith({
        title: 'Мой опрос',
        description: undefined,
        mode: 'SIMPLE',
        participantEmails: ['ivan@example.com'],
        options: [],
      })
    })
  })

  it('shows success toast and closes dialog on successful create', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.create).mockResolvedValueOnce({
      id: '1',
      title: 'Мой опрос',
      mode: 'SIMPLE',
      status: 'PENDING',
      currentRound: 1,
      isCreator: true,
      createdAt: new Date().toISOString(),
      description: null,
      participants: [],
      options: [],
      lastResult: null,
    })

    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByLabelText(/Название/i), 'Мой опрос')

    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^Создать$/i }))

    await waitFor(() => {
      expect(toast).toHaveBeenCalledWith('Голосование создано!', 'Мой опрос')
    })

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('shows error toast when API call fails', async () => {
    const { votesApi } = await import('@/api/votes')
    const { toast } = await import('@/components/ui/toaster')
    vi.mocked(votesApi.create).mockRejectedValueOnce(new Error('Server error'))

    const user = userEvent.setup()
    render(<CreateVoteDialog />, { wrapper: createWrapper() })
    await user.click(screen.getByRole('button', { name: /Создать голосование/i }))

    await user.type(screen.getByLabelText(/Название/i), 'Мой опрос')

    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^Создать$/i }))

    await waitFor(() => {
      expect(toast).toHaveBeenCalledWith('Ошибка', 'Server error', 'error')
    })
  })
})
