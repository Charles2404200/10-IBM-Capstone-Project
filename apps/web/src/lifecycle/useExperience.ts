/**
 * The hook form of [experienceStage]. Kept apart from the derivation itself so
 * a unit test of the rule does not have to pull react-query and the API client
 * in behind it.
 */
import { useMemo } from 'react'
import { useMyEngagements } from '@/api/hooks/useEngagements'
import { usePortfolioSummary } from '@/api/hooks/usePortfolio'
import { isActiveEngagement } from '@/features/engagement/services/engagementLifecycleService'
import { experienceStage, type ExperienceStage } from './experience'

export interface UseExperienceResult {
  stage: ExperienceStage
  /** True until the data needed to tell them apart has arrived. */
  isLoading: boolean
  /** Convenience for the common "explain this step" test. */
  isFirstEngagement: boolean
}

/**
 * While the portfolio is still loading, report RETURNING. Guessing the other
 * way would flash a first-run welcome at someone with fifty completed runs,
 * which is worse than a returning user briefly seeing no extra help.
 */
export function useExperience(): UseExperienceResult {
  const { data: portfolio, isLoading: portfolioLoading } = usePortfolioSummary()
  const { data: engagements, isLoading: engagementsLoading } = useMyEngagements()

  const isLoading = portfolioLoading || engagementsLoading

  const stage = useMemo<ExperienceStage>(() => {
    if (isLoading || !portfolio) return 'RETURNING'
    return experienceStage({
      completedEngagements: portfolio.completedEngagements,
      activeCount: (engagements ?? []).filter(isActiveEngagement).length,
    })
  }, [isLoading, portfolio, engagements])

  return {
    stage,
    isLoading,
    isFirstEngagement: stage === 'FIRST_ENGAGEMENT' || stage === 'FIRST_VISIT',
  }
}
