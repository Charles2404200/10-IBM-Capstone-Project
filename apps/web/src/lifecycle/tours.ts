/**
 * The guided walkthroughs, named once.
 *
 * A tour's id is what gets written to a learner's progress, so it has to
 * outlive any rename of the screen it runs on. Adding a workspace tour means
 * adding its id here; a learner who has finished every id in this list has
 * finished onboarding.
 */
export const TOUR_IDS = [
  'client-intelligence',
  'outreach-workspace',
  'meeting-preparation',
  'live-meeting',
  'proposal-studio',
] as const

export type TourId = (typeof TOUR_IDS)[number]

/** True once the learner has completed every walkthrough the product ships. */
export function hasCompletedAllTours(completed: readonly string[]): boolean {
  return TOUR_IDS.every((id) => completed.includes(id))
}
