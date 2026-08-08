import { describe, expect, it } from 'vitest'
import { shouldInvalidateSession } from './authSession'

describe('shouldInvalidateSession', () => {
  it('keeps the session when an anonymous login request is rejected', () => {
    expect(shouldInvalidateSession(401, undefined, 'new-token')).toBe(false)
  })

  it('keeps a new session when an older authenticated request finishes late', () => {
    expect(shouldInvalidateSession(401, 'Bearer old-token', 'new-token')).toBe(false)
  })

  it('invalidates the session rejected with its current token', () => {
    expect(shouldInvalidateSession(401, 'Bearer current-token', 'current-token')).toBe(true)
  })

  it('does not invalidate the session for authorization failures', () => {
    expect(shouldInvalidateSession(403, 'Bearer current-token', 'current-token')).toBe(false)
  })
})