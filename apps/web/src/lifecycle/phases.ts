/**
 * The engagement lifecycle: its order, its names, and what each step is for.
 *
 * One module so there is one answer. Before this a single step answered to four
 * different names depending on which screen you were on — the stepper said one
 * thing, the page title another, the progress bar a third — and nothing tells a
 * first-time learner those are the same step.
 */
import type { EngagementPhase } from '@/api/types'

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

/**
 * **The** name of each phase. Not "a" label — the only one.
 *
 * Before this, a single phase answered to four different names depending on
 * where you were standing: the stepper said "Make contact", the room said
 * "Outreach Desk", the page said "Outreach Workspace" and the progress bar said
 * "Outreach". A first-time learner has no way to know those are one thing, and
 * that — far more than any missing feature — is why the product reads as
 * confusing. Every surface now renders this string.
 *
 * Kept free of consulting jargon, and short enough to survive a stepper on a
 * phone. `phaseNameMatchesStations` in the tests fails the build if a room ever
 * drifts from this list again.
 */
export const PHASE_LABEL: Record<EngagementPhase, string> = {
  LEAD: 'Choose a client',
  CLIENT_INTELLIGENCE: 'Research the client',
  OUTREACH: 'Make contact',
  MEETING_PREPARATION: 'Prepare',
  LIVE_MEETING: 'The meeting',
  MEETING_REVIEW: 'Debrief',
  PROPOSAL: 'Proposal',
  OUTCOME: 'Their decision',
  REVIEW: 'Your review',
  COMPLETED: 'Portfolio',
}

/** Total steps in the lifecycle. The single source for "N of M phases". */
export const PHASE_COUNT = PHASE_ORDER.length

export function phaseIndex(phase: EngagementPhase): number {
  const index = PHASE_ORDER.indexOf(phase)
  return index === -1 ? 0 : index
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

/**
 * Which phase's *page* the learner is currently looking at.
 *
 * This is not the same thing as the engagement's phase, and conflating the two
 * was a real defect: after walking back to an earlier step, the stepper kept
 * highlighting the step the engagement had reached rather than the page on
 * screen, so it pointed somewhere the learner was not. The bar now marks the
 * page you are on, and shows separately how far the engagement itself has got.
 */
export function phaseFromPath(pathname: string): EngagementPhase | null {
  const tail = /\/dashboard\/engagements\/[^/]+\/([^/?#]+)/.exec(pathname)?.[1]
  if (!tail) return null
  switch (tail) {
    case 'leads':
      return 'LEAD'
    case 'intelligence':
      return 'CLIENT_INTELLIGENCE'
    case 'outreach':
      return 'OUTREACH'
    case 'preparation':
      return 'MEETING_PREPARATION'
    case 'meetings':
      return 'LIVE_MEETING'
    case 'proposal':
      return 'PROPOSAL'
    case 'assessment':
      return 'REVIEW'
    default:
      return null
  }
}

/** True on the screens that belong to no single engagement. */
export function isEngagementRoute(pathname: string): boolean {
  return /\/dashboard\/engagements\//.test(pathname)
}
