/**
 * The rule itself is covered in experience.test.ts. This covers the part only
 * the hook can get wrong: what it reports before the data it needs has arrived,
 * and whether it moves on once an engagement is finished.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import type { Engagement, PortfolioSummary } from '@/api/types'
import { useMyEngagements } from '@/api/hooks/useEngagements'
import { usePortfolioSummary } from '@/api/hooks/usePortfolio'
import { useExperience } from './useExperience'

vi.mock('@/api/hooks/useEngagements', () => ({ useMyEngagements: vi.fn() }))
vi.mock('@/api/hooks/usePortfolio', () => ({ usePortfolioSummary: vi.fn() }))

function portfolio(completedEngagements: number) {
  return { completedEngagements } as PortfolioSummary
}

function engagement(state: Engagement['state']) {
  return { id: state, state } as Engagement
}

/** Sets what each query has returned at this point in the render. */
function given({
  portfolioData,
  engagementData,
  loading = false,
}: {
  portfolioData?: PortfolioSummary
  engagementData?: Engagement[]
  loading?: boolean
}) {
  vi.mocked(usePortfolioSummary).mockReturnValue({
    data: portfolioData,
    isLoading: loading,
  } as never)
  vi.mocked(useMyEngagements).mockReturnValue({
    data: engagementData,
    isLoading: loading,
  } as never)
}

describe('useExperience', () => {
  beforeEach(() => vi.clearAllMocks())

  it('reports a returning learner while the data is still loading', () => {
    given({ loading: true })

    const { result } = renderHook(() => useExperience())

    expect(result.current.stage).toBe('RETURNING')
    expect(result.current.isLoading).toBe(true)
  })

  /**
   * The direction of the guess matters. Reporting a first visit too early
   * greets someone with fifty completed runs as a newcomer; reporting a
   * returning learner too early only withholds help for a moment.
   */
  it('reports a returning learner when the portfolio is missing entirely', () => {
    given({ portfolioData: undefined, engagementData: [] })

    const { result } = renderHook(() => useExperience())

    expect(result.current.stage).toBe('RETURNING')
  })

  it('reports a first visit once the data says nothing has been started', () => {
    given({ portfolioData: portfolio(0), engagementData: [] })

    const { result } = renderHook(() => useExperience())

    expect(result.current.stage).toBe('FIRST_VISIT')
    expect(result.current.isLoading).toBe(false)
    expect(result.current.isFirstEngagement).toBe(true)
  })

  it('reports a first engagement while one is open and none is finished', () => {
    given({ portfolioData: portfolio(0), engagementData: [engagement('CLIENT_INTELLIGENCE')] })

    const { result } = renderHook(() => useExperience())

    expect(result.current.stage).toBe('FIRST_ENGAGEMENT')
    expect(result.current.isFirstEngagement).toBe(true)
  })

  it('moves on once an engagement has been completed', () => {
    given({ portfolioData: portfolio(0), engagementData: [engagement('CLIENT_INTELLIGENCE')] })
    const { result, rerender } = renderHook(() => useExperience())
    expect(result.current.stage).toBe('FIRST_ENGAGEMENT')

    given({ portfolioData: portfolio(1), engagementData: [engagement('COMPLETED')] })
    rerender()

    expect(result.current.stage).toBe('RETURNING')
    expect(result.current.isFirstEngagement).toBe(false)
  })

  it('does not count a finished or failed run as one still in flight', () => {
    given({
      portfolioData: portfolio(0),
      engagementData: [engagement('COMPLETED'), engagement('MEETING_FAILED')],
    })

    const { result } = renderHook(() => useExperience())

    expect(result.current.stage).toBe('FIRST_VISIT')
  })

  /**
   * The three states are a sequence a learner passes through once, so the card
   * they belong to asks for the whole walk rather than one hop.
   */
  it('walks the whole lifecycle in order', () => {
    given({ portfolioData: portfolio(0), engagementData: [] })
    const { result, rerender } = renderHook(() => useExperience())
    expect(result.current.stage).toBe('FIRST_VISIT')

    given({ portfolioData: portfolio(0), engagementData: [engagement('OUTREACHING')] })
    rerender()
    expect(result.current.stage).toBe('FIRST_ENGAGEMENT')

    given({ portfolioData: portfolio(1), engagementData: [engagement('COMPLETED')] })
    rerender()
    expect(result.current.stage).toBe('RETURNING')

    // And does not fall back, however many new runs are opened afterwards.
    given({
      portfolioData: portfolio(1),
      engagementData: [engagement('COMPLETED'), engagement('QUALIFYING')],
    })
    rerender()
    expect(result.current.stage).toBe('RETURNING')
  })
})
