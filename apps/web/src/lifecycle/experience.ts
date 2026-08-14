/**
 * How much of this product the person in front of it has already seen.
 *
 * Before this there was no such notion anywhere in the app, so every screen was
 * built for someone with history. A brand-new account was greeted "Welcome
 * back — continue your current engagement" when it had never been here and had
 * nothing to continue, and was handed a catalogue of two thousand scenarios to
 * choose between with no basis for choosing.
 *
 * Deliberately derived rather than stored. A first-run flag on the server is a
 * second source of truth that drifts: it survives a reset, it lies after an
 * admin wipes engagements, and it needs a migration. The portfolio already
 * knows how many engagements someone has finished, and that is the fact this
 * is actually about.
 *
 * What it must never become is a difficulty setting. The guidance it unlocks
 * says where the interface is, never what the consultant should decide — the
 * second is the thing being assessed, and an app that answers it is scoring
 * itself.
 */
export type ExperienceStage =
  /** Has never started anything. Needs to be told what this is and given one move. */
  | 'FIRST_VISIT'
  /** Is inside their first engagement. Still needs each step explained on arrival. */
  | 'FIRST_ENGAGEMENT'
  /** Has finished at least one. Explanations are now repetition. */
  | 'RETURNING'

export interface ExperienceInput {
  /** From the portfolio summary. */
  completedEngagements: number
  /** How many engagements are currently in flight. */
  activeCount: number
}

export function experienceStage({ completedEngagements, activeCount }: ExperienceInput): ExperienceStage {
  if (completedEngagements > 0) return 'RETURNING'
  return activeCount > 0 ? 'FIRST_ENGAGEMENT' : 'FIRST_VISIT'
}
