import { renderHook, waitFor, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTelegramLink } from '../useTelegramLink'
import { telegramApi } from '@/api/telegram'
import { toast } from '@/components/ui/toaster'

vi.mock('@/api/telegram', () => ({
  telegramApi: {
    getLinkToken: vi.fn(),
  },
}))

vi.mock('@/components/ui/toaster', () => ({
  toast: vi.fn(),
}))

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useTelegramLink', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.assign(navigator, { clipboard: { writeText: vi.fn() } })
  })

  it('starts with no token', () => {
    const { result } = renderHook(() => useTelegramLink(), { wrapper: createWrapper() })
    expect(result.current.token).toBeNull()
  })

  it('stores the token on successful fetch', async () => {
    vi.mocked(telegramApi.getLinkToken).mockResolvedValueOnce({
      token: 'abc123',
      expiresAt: '2026-01-01T00:00:00Z',
    })

    const { result } = renderHook(() => useTelegramLink(), { wrapper: createWrapper() })

    act(() => result.current.getLinkToken.mutate())

    await waitFor(() => expect(result.current.token).toBe('abc123'))
  })

  it('shows an error toast when fetching the token fails', async () => {
    vi.mocked(telegramApi.getLinkToken).mockRejectedValueOnce(new Error('network error'))

    const { result } = renderHook(() => useTelegramLink(), { wrapper: createWrapper() })

    act(() => result.current.getLinkToken.mutate())

    await waitFor(() => expect(result.current.getLinkToken.isError).toBe(true))
    expect(toast).toHaveBeenCalledWith('Ошибка', 'Не удалось получить токен', 'error')
    expect(result.current.token).toBeNull()
  })

  it('copies the link command to the clipboard and clears the token', async () => {
    vi.mocked(telegramApi.getLinkToken).mockResolvedValueOnce({
      token: 'abc123',
      expiresAt: '2026-01-01T00:00:00Z',
    })

    const { result } = renderHook(() => useTelegramLink(), { wrapper: createWrapper() })
    act(() => result.current.getLinkToken.mutate())
    await waitFor(() => expect(result.current.token).toBe('abc123'))

    act(() => result.current.copyToken())
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('/link abc123')
    expect(toast).toHaveBeenCalledWith('Скопировано!', 'Отправьте эту команду боту')

    act(() => result.current.clearToken())
    expect(result.current.token).toBeNull()
  })
})
