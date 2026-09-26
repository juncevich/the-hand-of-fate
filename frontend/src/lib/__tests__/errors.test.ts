import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { extractErrorMessage, onMutationError } from '../errors'
import { winnerLabel } from '../utils'

vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))

function axiosError(data: unknown, message = 'Request failed') {
  return new AxiosError(message, 'ERR_BAD_REQUEST', undefined, undefined, {
    data,
    status: 400,
    statusText: 'Bad Request',
    headers: {},
    config: { headers: new AxiosHeaders() },
  })
}

describe('extractErrorMessage', () => {
  it('prefers the ProblemDetail title', () => {
    expect(extractErrorMessage(axiosError({ title: 'Vote not found', detail: 'ignored' }))).toBe('Vote not found')
  })

  it('falls back to the ProblemDetail detail', () => {
    expect(extractErrorMessage(axiosError({ detail: 'Details here' }))).toBe('Details here')
  })

  it('falls back to the axios message when the body has neither', () => {
    expect(extractErrorMessage(axiosError(undefined, 'Network Error'))).toBe('Network Error')
  })

  it('uses the message of a plain Error', () => {
    expect(extractErrorMessage(new Error('boom'))).toBe('boom')
  })

  it('returns a generic message for non-errors', () => {
    expect(extractErrorMessage('oops')).toBe('Неизвестная ошибка')
    expect(extractErrorMessage(null)).toBe('Неизвестная ошибка')
  })
})

describe('onMutationError', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows an error toast with the extracted message', async () => {
    const { toast } = await import('@/components/ui/toaster')
    onMutationError(axiosError({ title: 'Cannot modify a non-pending vote' }))
    expect(toast).toHaveBeenCalledWith('Ошибка', 'Cannot modify a non-pending vote', 'error')
  })
})

describe('winnerLabel', () => {
  it('prefers option title, then display name, then email', () => {
    expect(winnerLabel({ winnerOptionTitle: 'Pizza', winnerDisplayName: 'Alice', winnerEmail: 'a@x.y' })).toBe('Pizza')
    expect(winnerLabel({ winnerOptionTitle: null, winnerDisplayName: 'Alice', winnerEmail: 'a@x.y' })).toBe('Alice')
    expect(winnerLabel({ winnerOptionTitle: null, winnerDisplayName: null, winnerEmail: 'a@x.y' })).toBe('a@x.y')
  })

  it('returns a dash when there is no winner info', () => {
    expect(winnerLabel({})).toBe('—')
  })
})
