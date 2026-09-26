import { describe, it, expect, beforeEach } from 'vitest'
import { useThemeStore } from '../themeStore'

describe('themeStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useThemeStore.setState({ theme: 'dark' })
  })

  it('defaults to dark', () => {
    expect(useThemeStore.getState().theme).toBe('dark')
  })

  it('toggles between dark and light', () => {
    useThemeStore.getState().toggleTheme()
    expect(useThemeStore.getState().theme).toBe('light')

    useThemeStore.getState().toggleTheme()
    expect(useThemeStore.getState().theme).toBe('dark')
  })

  it('persists the chosen theme to localStorage', () => {
    useThemeStore.getState().toggleTheme()

    const stored = JSON.parse(localStorage.getItem('fate-theme') ?? '{}') as { state?: { theme?: string } }
    expect(stored.state?.theme).toBe('light')
  })
})
