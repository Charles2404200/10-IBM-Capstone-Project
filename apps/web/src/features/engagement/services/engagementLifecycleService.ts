import type { Engagement } from '@/api/types'

/** Learner-facing lifecycle semantics shared by engagement list views. */
export function isActiveEngagement(engagement: Engagement): boolean {
  return engagement.state !== 'COMPLETED' && engagement.state !== 'MEETING_FAILED'
}

export function requiresMeetingRetry(engagement: Engagement): boolean {
  return engagement.state === 'MEETING_FAILED'
}
