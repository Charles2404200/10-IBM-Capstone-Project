import { describe, expect, it } from 'vitest'
import { TOUR_IDS, hasCompletedAllTours } from './tours'

describe('hasCompletedAllTours', () => {
  it('is false while any walkthrough is outstanding', () => {
    expect(hasCompletedAllTours(TOUR_IDS.slice(0, -1))).toBe(false)
  })

  it('is true once every walkthrough is recorded', () => {
    expect(hasCompletedAllTours([...TOUR_IDS])).toBe(true)
  })

  it('ignores unknown ids left over from a removed walkthrough', () => {
    expect(hasCompletedAllTours([...TOUR_IDS, 'retired-tour'])).toBe(true)
  })
})
