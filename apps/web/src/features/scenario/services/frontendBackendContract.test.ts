import { describe, expect, it } from 'vitest'
import type { GameplayDifficultyProfile, LeadAuthoringView, ScenarioSummary } from '@/api/types'
import { achievementDescription } from '@/features/achievement/achievementPresentation'
import { GAMEPLAY_DIFFICULTY_RANGES, gameplayDifficultyFromScenario, withGameplayNumber } from './gameplayDifficultyContract'
import { leadAuthoringFormFrom } from './scenarioAuthoringContract'

const backendProfile: GameplayDifficultyProfile = {
  level: 'HARD',
  researchArtifactsPerAction: 8,
  distractorArtifactsPerAction: 7,
  contradictionCount: 6,
  initialTrust: 100,
  initialInterest: 99,
  initialPatience: 98,
  meetingTurnLimit: 20,
  budgetVisible: true,
  timelinePressureDays: 90,
  requiredEvidenceCount: 8,
  requiredConfidencePercent: 90,
  outreachAcceptanceThreshold: 95,
  proposalEvidenceCoverageThreshold: 95,
  personaResistance: 100,
  scoringTolerance: 130,
}

describe('new frontend/backend authoring contracts', () => {
  it('uses the required backend difficulty profile without replacing valid values', () => {
    const scenario = { gameplayDifficulty: backendProfile } satisfies Pick<ScenarioSummary, 'gameplayDifficulty'>

    expect(gameplayDifficultyFromScenario(scenario)).toBe(backendProfile)
    expect(scenario.gameplayDifficulty.initialTrust).toBe(100)
  })

  it('keeps frontend numeric bounds aligned with the validated backend request', () => {
    expect(GAMEPLAY_DIFFICULTY_RANGES).toEqual({
      researchArtifactsPerAction: { min: 2, max: 8 },
      distractorArtifactsPerAction: { min: 0, max: 7 },
      contradictionCount: { min: 0, max: 6 },
      initialTrust: { min: 0, max: 100 },
      initialInterest: { min: 0, max: 100 },
      initialPatience: { min: 0, max: 100 },
      meetingTurnLimit: { min: 4, max: 20 },
      timelinePressureDays: { min: 1, max: 90 },
      requiredEvidenceCount: { min: 2, max: 8 },
      requiredConfidencePercent: { min: 20, max: 90 },
      outreachAcceptanceThreshold: { min: 50, max: 95 },
      proposalEvidenceCoverageThreshold: { min: 30, max: 95 },
      personaResistance: { min: 0, max: 100 },
      scoringTolerance: { min: 70, max: 130 },
    })
  })

  it('keeps distractor counts below a reduced research-artifact count', () => {
    const adjusted = withGameplayNumber(backendProfile, 'researchArtifactsPerAction', 2)

    expect(adjusted.researchArtifactsPerAction).toBe(2)
    expect(adjusted.distractorArtifactsPerAction).toBe(1)
  })

  it('normalizes nullable draft lead fields for controlled form inputs', () => {
    const draft: LeadAuthoringView = {
      id: 'lead-1',
      companyName: 'Example Corp',
      industry: 'Technology',
      publicDescription: null,
      difficulty: 'MEDIUM',
      potentialValueRange: null,
      decisionMaker: null,
      technologyStack: null,
      budgetSignal: null,
      painSeverity: null,
      signals: [],
    }

    expect(leadAuthoringFormFrom(draft)).toMatchObject({
      publicDescription: '',
      potentialValueRange: '',
      decisionMaker: '',
      technologyStack: '',
      budgetSignal: '',
      painSeverity: '',
      signals: [],
    })
  })

  it('renders optional achievement descriptions safely', () => {
    expect(achievementDescription(null)).toBe('No description provided.')
    expect(achievementDescription('  ')).toBe('No description provided.')
    expect(achievementDescription('Complete the first engagement')).toBe('Complete the first engagement')
  })
})
