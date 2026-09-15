import { describe, expect, it } from 'vitest'
import { resolveEngagementRoute } from './engagementRouting'
import type { Engagement } from './types'

function engagement(overrides: Partial<Engagement>): Engagement {
  return {
    id: 'engagement-1',
    userId: 'user-1',
    scenarioId: 'scenario-1',
    personaId: 'persona-1',
    state: 'DISCOVERY_COMPLETE',
    selectedLeadId: 'lead-1',
    createdAt: '2026-09-15T00:00:00Z',
    completedAt: null,
    events: [],
    scenarioTitle: 'Lifecycle scenario',
    scenarioIndustry: 'Technology',
    leadCompanyName: 'Example client',
    phase: 'MEETING_REVIEW',
    phaseLabel: 'Debrief the conversation',
    progressPercent: 60,
    nextAction: 'Review the meeting',
    evidenceCount: 4,
    daysElapsed: 2,
    meetingId: 'meeting-7',
    ...overrides,
  }
}

describe('engagement routing', () => {
  it('routes meeting review to the actual meeting debrief', () => {
    expect(resolveEngagementRoute(engagement({ phase: 'MEETING_REVIEW' })))
      .toBe('/dashboard/engagements/engagement-1/meetings/meeting-7')
  })

  it('routes client outcome to the proposal decision view', () => {
    expect(resolveEngagementRoute(engagement({ phase: 'OUTCOME' })))
      .toBe('/dashboard/engagements/engagement-1/proposal')
  })
})
