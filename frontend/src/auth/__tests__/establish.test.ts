import { describe, expect, it } from 'vitest'
import { safeReturnTo } from '../establish'

describe('safeReturnTo', () => {
  it('keeps paths of this app and turns anything else into the root', () => {
    expect(safeReturnTo('/votes/1?tab=history')).toBe('/votes/1?tab=history')
    expect(safeReturnTo('//evil.example/x')).toBe('/')
    expect(safeReturnTo('https://evil.example')).toBe('/')
    expect(safeReturnTo(undefined)).toBe('/')
    expect(safeReturnTo(42)).toBe('/')
  })
})
