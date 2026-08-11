/**
 * Which engagement the chrome should be talking about.
 *
 * Lives here rather than in a page because both the HUD and the world page need
 * it, and the HUD must not pull a page module into the app shell's bundle.
 */
import type { Engagement } from '@/api/types'

/** The engagement still in flight; falls back to the most recent one. */
export function selectActiveEngagement(engagements: Engagement[] | undefined): Engagement | null {
  if (!engagements || engagements.length === 0) return null
  const live = engagements.filter((e) => e.phase !== 'COMPLETED')
  const pool = live.length > 0 ? live : engagements
  return [...pool].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )[0]
}
