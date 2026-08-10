/**
 * Turns the server's engagement phase into the world's lock state.
 *
 * The rule is intentionally strict: a station is walkable-and-enterable only if
 * the engagement has reached it. Being able to *see* the locked rooms is the
 * point — it answers "what happens after this?" without letting the learner
 * skip the work, which is exactly the visibility the flat dashboard never gave.
 */
import type { Engagement, EngagementPhase } from '@/api/types'

/** Canonical order of the engagement lifecycle, matching the backend enum. */
export const PHASE_ORDER: readonly EngagementPhase[] = [
  'LEAD',
  'CLIENT_INTELLIGENCE',
  'OUTREACH',
  'MEETING_PREPARATION',
  'LIVE_MEETING',
  'MEETING_REVIEW',
  'PROPOSAL',
  'OUTCOME',
  'REVIEW',
  'COMPLETED',
]

/** Human labels, kept free of consulting jargon for first-time players. */
export const PHASE_LABEL: Record<EngagementPhase, string> = {
  LEAD: 'Pick a client',
  CLIENT_INTELLIGENCE: 'Research them',
  OUTREACH: 'Make contact',
  MEETING_PREPARATION: 'Prepare',
  LIVE_MEETING: 'Meet them',
  MEETING_REVIEW: 'Debrief',
  PROPOSAL: 'Propose',
  OUTCOME: 'Their decision',
  REVIEW: 'Your review',
  COMPLETED: 'Done',
}

export type StationStatus = 'done' | 'current' | 'locked' | 'open'

export function phaseIndex(phase: EngagementPhase): number {
  const index = PHASE_ORDER.indexOf(phase)
  return index === -1 ? 0 : index
}

/**
 * Status of a station given the engagement's current phase.
 * `phase === null` marks always-open rooms (Command Centre, Portfolio).
 */
export function stationStatus(
  stationPhase: EngagementPhase | null,
  engagement: Engagement | null
): StationStatus {
  if (stationPhase === null) return 'open'
  if (!engagement) return stationPhase === 'LEAD' ? 'current' : 'locked'
  const current = phaseIndex(engagement.phase)
  const target = phaseIndex(stationPhase)
  if (target < current) return 'done'
  if (target === current) return 'current'
  return 'locked'
}

/** Progress across the whole lifecycle, 0..1 — drives the HUD stepper fill. */
export function lifecycleProgress(engagement: Engagement | null): number {
  if (!engagement) return 0
  return phaseIndex(engagement.phase) / (PHASE_ORDER.length - 1)
}

/**
 * One-sentence brief for the phase the learner is standing in.
 *
 * These exist because the current app drops learners into a workspace with no
 * statement of what "done" looks like. Every string answers three things: what
 * this step is for, what you produce, and what unlocks next.
 */
export const PHASE_BRIEF: Record<EngagementPhase, { goal: string; done: string; next: string }> = {
  LEAD: {
    goal: 'Choose which client opportunity to pursue, on deliberately thin information.',
    done: 'You have committed to one lead.',
    next: 'The research library opens so you can find out who you just committed to.',
  },
  CLIENT_INTELLIGENCE: {
    goal: 'Gather evidence about the client and commit to a hypothesis about their real problem.',
    done: 'Enough corroborated evidence, a named stakeholder, and a submitted hypothesis.',
    next: 'The outreach desk opens so you can contact them with something to say.',
  },
  OUTREACH: {
    goal: 'Earn a meeting by email. One clear reason, one low-friction ask.',
    done: 'The client agrees to meet.',
    next: 'The prep room opens so you can decide what the meeting is for.',
  },
  MEETING_PREPARATION: {
    goal: 'Set an objective, an agenda, and the questions that will actually reveal something.',
    done: 'Your prep clears the readiness threshold.',
    next: 'The meeting room opens. The client is waiting.',
  },
  LIVE_MEETING: {
    goal: 'Run the conversation. Build trust, hold their interest, do not burn their patience.',
    done: 'You leave with the discovery you needed and the relationship intact.',
    next: 'The debrief nook opens so you can see what that conversation cost and earned.',
  },
  MEETING_REVIEW: {
    goal: 'Look back at the conversation: what you uncovered, what you left on the table.',
    done: 'You have read the debrief.',
    next: 'The proposal studio opens.',
  },
  PROPOSAL: {
    goal: 'Turn evidence into a recommendation with a budget, a timeline and named risks.',
    done: 'A submitted proposal grounded in evidence you actually collected.',
    next: 'The client decides.',
  },
  OUTCOME: {
    goal: 'Receive the client decision. Research, relationship and proposal all count here.',
    done: 'The client has responded.',
    next: 'Your performance review is generated.',
  },
  REVIEW: {
    goal: 'Read a structured assessment of how you performed and what to work on.',
    done: 'You have reviewed your competency scores.',
    next: 'The engagement joins your portfolio.',
  },
  COMPLETED: {
    goal: 'This engagement is closed and recorded.',
    done: 'Nothing further — start another scenario to keep building the portfolio.',
    next: 'Harder scenarios are worth attempting once this one is behind you.',
  },
}
