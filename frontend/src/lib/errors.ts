import { AxiosError } from 'axios'
import { toast } from '@/components/ui/toaster'
import { ERROR_MESSAGES, fieldErrorMessage, type ErrorCode } from '@/lib/errorMessages'

interface Problem {
  title?: string
  detail?: string
  code?: string
  fieldErrors?: Record<string, string>
}

function problemOf(error: unknown): Problem | undefined {
  return error instanceof AxiosError ? (error.response?.data as Problem | undefined) : undefined
}

function isErrorCode(code: unknown): code is ErrorCode {
  return typeof code === 'string' && code in ERROR_MESSAGES
}

/** The problem's machine-readable `code` (e.g. `EMAIL_NOT_VERIFIED`), when the backend sent one. */
export function errorCode(error: unknown): ErrorCode | undefined {
  const code = problemOf(error)?.code
  return isErrorCode(code) ? code : undefined
}

/** User-facing text: the translation of the code, else the backend's (English) title. */
export function extractErrorMessage(error: unknown): string {
  const code = errorCode(error)
  if (code) return ERROR_MESSAGES[code]
  const data = problemOf(error)
  if (data?.title) return data.title
  if (data?.detail) return data.detail
  if (error instanceof Error) return error.message
  return 'Неизвестная ошибка'
}

/** Translated per-field validation errors of a 400 VALIDATION_FAILED, keyed by field name. */
export function extractFieldErrors(error: unknown): Record<string, string> {
  const fields = problemOf(error)?.fieldErrors ?? {}
  return Object.fromEntries(Object.entries(fields).map(([field, constraint]) => [field, fieldErrorMessage(field, constraint)]))
}

export function onMutationError(error: unknown) {
  toast('Ошибка', extractErrorMessage(error), 'error')
}
