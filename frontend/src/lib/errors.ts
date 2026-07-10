import { AxiosError } from 'axios'
import { toast } from '@/components/ui/toaster'

export function extractErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as { title?: string; detail?: string } | undefined
    if (data?.title) return data.title
    if (data?.detail) return data.detail
  }
  if (error instanceof Error) return error.message
  return 'Неизвестная ошибка'
}

export function onMutationError(error: unknown) {
  toast('Ошибка', extractErrorMessage(error), 'error')
}
