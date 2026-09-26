import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { ErrorState } from '../error-state'

describe('ErrorState', () => {
  it('shows the message without a retry button by default', () => {
    render(<ErrorState message="Сервер недоступен" />)

    expect(screen.getByText('Не удалось загрузить данные')).toBeInTheDocument()
    expect(screen.getByText('Сервер недоступен')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Повторить' })).not.toBeInTheDocument()
  })

  it('calls onRetry when retry is clicked', async () => {
    const onRetry = vi.fn()
    render(<ErrorState message="Ошибка" onRetry={onRetry} />)

    await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))

    expect(onRetry).toHaveBeenCalledOnce()
  })
})
