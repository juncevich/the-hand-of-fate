import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { Toaster, toast, useToastStore } from '../toaster'

describe('Toaster', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] })
  })

  it('renders queued toasts with title and description', () => {
    render(<Toaster />)

    act(() => {
      toast('Сохранено', 'Всё хорошо')
      toast('Без описания')
    })

    expect(screen.getByText('Сохранено')).toBeInTheDocument()
    expect(screen.getByText('Всё хорошо')).toBeInTheDocument()
    expect(screen.getByText('Без описания')).toBeInTheDocument()
  })

  it('removes a toast when its close button is clicked', () => {
    render(<Toaster />)
    act(() => toast('Ошибка', 'Что-то сломалось', 'error'))

    const close = screen.getByText('Ошибка').closest('li')!.querySelector('button')!
    fireEvent.click(close)

    expect(useToastStore.getState().toasts).toHaveLength(0)
    expect(screen.queryByText('Ошибка')).not.toBeInTheDocument()
  })
})
