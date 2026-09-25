import { describe, it, expect, beforeEach } from 'vitest'
import { toast, useToastStore } from '../toaster'

describe('toast store', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] })
  })

  it('adds a toast with title, description and variant', () => {
    toast('Saved', 'All good', 'error')

    expect(useToastStore.getState().toasts).toEqual([
      expect.objectContaining({ title: 'Saved', description: 'All good', variant: 'error' }),
    ])
  })

  it('gives every toast a distinct id', () => {
    for (let i = 0; i < 100; i++) toast(`t${i}`)

    const ids = useToastStore.getState().toasts.map((t) => t.id)
    expect(new Set(ids).size).toBe(100)
  })

  it('removes only the toast with the given id', () => {
    toast('first')
    toast('second')
    const [first, second] = useToastStore.getState().toasts

    useToastStore.getState().removeToast(first.id)

    expect(useToastStore.getState().toasts).toEqual([second])
  })
})
