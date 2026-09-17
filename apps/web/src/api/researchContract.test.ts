import { describe, expect, it } from 'vitest'
import type { SaveResearchPayload } from './types'

describe('research write contract', () => {
  it('keeps optional provenance hints compatible with the immutable frontend contract', () => {
    const hasOrigin: 'origin' extends keyof SaveResearchPayload ? true : false = true
    const hasVerificationStatus: 'verificationStatus' extends keyof SaveResearchPayload ? true : false = true
    const payload: SaveResearchPayload = {
      note: 'Learner evidence',
      evidenceType: 'COMPANY_NEWS',
      origin: 'USER_SUPPLIED',
      verificationStatus: 'UNVERIFIED',
    }

    expect(hasOrigin).toBe(true)
    expect(hasVerificationStatus).toBe(true)
    expect(payload.origin).toBe('USER_SUPPLIED')
    expect(payload.verificationStatus).toBe('UNVERIFIED')
  })
})
