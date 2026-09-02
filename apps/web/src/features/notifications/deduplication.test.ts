import { describe, expect, it } from 'vitest'
import { BoundedEventDeduplicator } from './deduplication'

describe('BoundedEventDeduplicator', () => {
  it('rejects duplicate event IDs without growing beyond its capacity', () => {
    const deduplicator = new BoundedEventDeduplicator(2)
    expect(deduplicator.remember('a')).toBe(true)
    expect(deduplicator.remember('a')).toBe(false)
    expect(deduplicator.remember('b')).toBe(true)
    expect(deduplicator.remember('c')).toBe(true)
    expect(deduplicator.size).toBe(2)
    expect(deduplicator.remember('a')).toBe(true)
  })
})

