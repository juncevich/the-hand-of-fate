import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ErrorBoundary } from '../ErrorBoundary'

function Boom(): never {
  throw new Error('kaboom')
}

describe('ErrorBoundary', () => {
  const assign = vi.fn()
  const originalLocation = window.location

  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    Object.defineProperty(window, 'location', { configurable: true, value: { ...originalLocation, assign } })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    Object.defineProperty(window, 'location', { configurable: true, value: originalLocation })
    assign.mockReset()
  })

  it('renders children when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>
    )

    expect(screen.getByText('All good')).toBeInTheDocument()
  })

  it('shows the fallback and logs the error when a child throws', () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    )

    expect(screen.getByText('Что-то пошло не так')).toBeInTheDocument()
    expect(console.error).toHaveBeenCalledWith('Unhandled UI error:', expect.any(Error), expect.anything())
  })

  it('navigates home from the fallback', async () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    )

    await userEvent.click(screen.getByRole('button', { name: 'На главную' }))

    expect(assign).toHaveBeenCalledWith('/')
  })
})
