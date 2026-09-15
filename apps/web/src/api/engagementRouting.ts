import type { Engagement, StageCapability } from '@/api/types'

/** Application-owned route mapping for server-resolved lifecycle capabilities. */
export function resolveStageRoute(capability: StageCapability, engagement: Engagement): string {
  const base = `/dashboard/engagements/${engagement.id}`
  switch (capability) {
    case 'LEAD': return `${base}/leads`
    case 'CLIENT_INTELLIGENCE': return `${base}/intelligence`
    case 'OUTREACH': return `${base}/outreach`
    case 'MEETING_PREPARATION': return `${base}/preparation`
    case 'LIVE_MEETING':
      return engagement.meetingId ? `${base}/meetings/${engagement.meetingId}` : `${base}/preparation`
    case 'MEETING_REVIEW':
      return engagement.meetingId ? `${base}/meetings/${engagement.meetingId}` : `${base}/assessment`
    case 'PROPOSAL':
    case 'OUTCOME': return `${base}/proposal`
    case 'REVIEW': return `${base}/assessment`
    case 'COMPLETED': return '/dashboard/portfolio'
  }
}

/** Resolves the learner's Continue destination from authoritative engagement state. */
export function resolveEngagementRoute(engagement: Engagement): string {
  const base = `/dashboard/engagements/${engagement.id}`
  if (engagement.state === 'MEETING_FAILED') {
    return engagement.meetingId ? `${base}/meetings/${engagement.meetingId}` : `${base}/leads`
  }
  // Preserve the established post-completion Continue behavior. The HUD has a
  // separate explicit Portfolio stage jump.
  if (engagement.phase === 'COMPLETED') return `${base}/assessment`
  return resolveStageRoute(engagement.phase, engagement)
}
