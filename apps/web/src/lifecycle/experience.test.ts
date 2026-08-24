import { describe, expect, it } from 'vitest'
import { experienceStage } from './experience'

describe('experienceStage', () => {
  it('treats an account with nothing started as a first visit', () => {
    expect(experienceStage({ completedEngagements: 0, activeCount: 0 })).toBe('FIRST_VISIT')
  })

  it('treats an account mid-way through its only engagement as a first engagement', () => {
    expect(experienceStage({ completedEngagements: 0, activeCount: 1 })).toBe('FIRST_ENGAGEMENT')
  })

  it('stops explaining once one engagement has been completed', () => {
    expect(experienceStage({ completedEngagements: 1, activeCount: 0 })).toBe('RETURNING')
    expect(experienceStage({ completedEngagements: 1, activeCount: 3 })).toBe('RETURNING')
  })

  /**
   * The guard that matters. Someone who abandons their first run and starts
   * several more has still never finished one, so the step explanations stay —
   * an abandoned run is evidence they were not ready, not evidence they were.
   */
  it('keeps explaining while several runs are open but none has finished', () => {
    expect(experienceStage({ completedEngagements: 0, activeCount: 4 })).toBe('FIRST_ENGAGEMENT')
  })
})
