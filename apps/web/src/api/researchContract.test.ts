import { describe, expect, it } from 'vitest'
import type { SaveResearchPayload } from './types'

describe('research write contract', () => {
  it('does not expose server-owned provenance as writable fields', () => {
    const hasOrigin: 'origin' extends keyof SaveResearchPayload ? true : false = false
    const hasVerificationStatus: 'verificationStatus' extends keyof SaveResearchPayload ? true : false = false
    const payload: SaveResearchPayload = { note: 'Learner evidence', evidenceType: 'COMPANY_NEWS' }

    expect(hasOrigin).toBe(false)
    expect(hasVerificationStatus).toBe(false)
    expect(payload).toEqual({ note: 'Learner evidence', evidenceType: 'COMPANY_NEWS' })
  })
})
