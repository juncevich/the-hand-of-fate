import { fireEvent, render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { UserAvatar } from '../UserAvatar'

describe('UserAvatar', () => {
  it('shows the image when the user has an avatar', () => {
    render(<UserAvatar avatarUrl="/a.png" displayName="alice" />)

    expect(screen.getByRole('img', { name: 'Аватар alice' })).toHaveAttribute('src', '/a.png')
  })

  it('falls back to the initial without an avatar', () => {
    render(<UserAvatar avatarUrl={null} displayName="alice" />)

    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('falls back to the initial when the image fails, and retries a new url', () => {
    const { rerender } = render(<UserAvatar avatarUrl="/old.png" displayName="alice" />)

    fireEvent.error(screen.getByRole('img'))
    expect(screen.getByText('A')).toBeInTheDocument()

    rerender(<UserAvatar avatarUrl="/new.png" displayName={null} />)
    expect(screen.getByRole('img', { name: 'Аватар' })).toHaveAttribute('src', '/new.png')
  })
})
