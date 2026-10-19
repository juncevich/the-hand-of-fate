import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { errorCode, extractErrorMessage, extractFieldErrors, onMutationError } from '../errors'
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
  it('translates a known code instead of showing the English title', () => {
    const error = axiosError({ title: 'Vote not found', code: 'VOTE_NOT_FOUND' })
    expect(extractErrorMessage(error)).toBe('Голосование не найдено')
    expect(errorCode(error)).toBe('VOTE_NOT_FOUND')
  })

  it('falls back to the title for a code it does not know', () => {
    const error = axiosError({ title: 'Something new', code: 'FROM_A_NEWER_BACKEND' })
    expect(extractErrorMessage(error)).toBe('Something new')
    expect(errorCode(error)).toBeUndefined()
  })

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

describe('extractFieldErrors', () => {
  it('translates the violated constraint of each field', () => {
    const error = axiosError({ code: 'VALIDATION_FAILED', fieldErrors: { email: 'Email', password: 'Size', displayName: 'NotBlank' } })
    expect(extractFieldErrors(error)).toEqual({
      email: 'Некорректный email',
      password: 'От 8 до 100 символов',
      displayName: 'Обязательное поле',
    })
  })

  it('is empty without field errors', () => {
    expect(extractFieldErrors(new Error('x'))).toEqual({})
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
