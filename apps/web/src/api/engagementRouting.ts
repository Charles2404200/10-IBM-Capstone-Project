import type { Engagement } from '@/api/types'

/**
 * Maps an engagement's current phase to the workspace route the learner
 * should land on when they click "Continue" — the single source of truth
 * for phase → route navigation, replacing ad-hoc hardcoded links.
 *
 * Each engagement always resumes exactly where it left off instead of
 * always routing back to Lead Pipeline (the earlier bug: continuing an
 * engagement that had already selected a lead sent the learner back to
 * "Investigate Lead", which then failed because a lead was already locked in).
 */
export function resolveEngagementRoute(engagement: Engagement): string {
  const base = `/dashboard/engagements/${engagement.id}`
  switch (engagement.phase) {
    case 'LEAD':
      return `${base}/leads`
    case 'CLIENT_INTELLIGENCE':
      return `${base}/intelligence`
    case 'OUTREACH':
      return `${base}/outreach`
    case 'MEETING_PREPARATION':
      return `${base}/preparation`
    case 'LIVE_MEETING':
      // Falls back to preparation if the meeting id hasn't been enriched yet —
      // still correct, since MeetingPreparationPage lets the learner resume
      // into the live meeting from there.
      return engagement.meetingId ? `${base}/meetings/${engagement.meetingId}` : `${base}/preparation`
    case 'PROPOSAL':
      return `${base}/proposal`
    case 'OUTCOME':
    case 'REVIEW':
      return `${base}/assessment`
    default:
      return `${base}/leads`
  }
}
