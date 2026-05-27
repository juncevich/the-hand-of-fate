import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { BackendStatusIndicator } from '../BackendStatusIndicator'

vi.mock('@/hooks/useBackendStatus')

import { useBackendStatus } from '@/hooks/useBackendStatus'

describe('BackendStatusIndicator', () => {
  it('shows connecting label while checking', () => {
    vi.mocked(useBackendStatus).mockReturnValue('checking')
    render(<BackendStatusIndicator />)

    const el = screen.getByRole('status')
    expect(el).toHaveAttribute('aria-label', 'Backend: connecting…')
    expect(el).toHaveClass('bg-yellow-400', 'animate-pulse')
  })

  it('shows online label when backend is reachable', () => {
    vi.mocked(useBackendStatus).mockReturnValue('online')
    render(<BackendStatusIndicator />)

    const el = screen.getByRole('status')
    expect(el).toHaveAttribute('aria-label', 'Backend: online')
    expect(el).toHaveClass('bg-green-400')
    expect(el).not.toHaveClass('animate-pulse')
  })

  it('shows offline label when backend is unreachable', () => {
    vi.mocked(useBackendStatus).mockReturnValue('offline')
    render(<BackendStatusIndicator />)

    const el = screen.getByRole('status')
    expect(el).toHaveAttribute('aria-label', 'Backend: offline')
    expect(el).toHaveClass('bg-red-500', 'animate-pulse')
  })
})
