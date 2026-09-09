import type { GameplayDifficultyProfile, ScenarioSummary } from '@/api/types'

export type NumericGameplayField = Exclude<keyof GameplayDifficultyProfile, 'level' | 'budgetVisible'>

/** Mirrors the validated backend request bounds; defaults and current values always come from the API. */
export const GAMEPLAY_DIFFICULTY_RANGES: Record<NumericGameplayField, { min: number; max: number }> = {
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
}

/** Preserves the backend-resolved profile exactly, including dimension-sensitive HARD defaults. */
export function gameplayDifficultyFromScenario(
  scenario: Pick<ScenarioSummary, 'gameplayDifficulty'>,
): GameplayDifficultyProfile {
  return scenario.gameplayDifficulty
}

export function withGameplayNumber(
  profile: GameplayDifficultyProfile,
  field: NumericGameplayField,
  value: number,
): GameplayDifficultyProfile {
  const next = { ...profile, [field]: value }
  if (field === 'researchArtifactsPerAction' && next.distractorArtifactsPerAction >= value) {
    next.distractorArtifactsPerAction = Math.max(
      GAMEPLAY_DIFFICULTY_RANGES.distractorArtifactsPerAction.min,
      value - 1,
    )
  }
  return next
}
