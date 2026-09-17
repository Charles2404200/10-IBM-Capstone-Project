import { describe, expect, it } from 'vitest'
import type { Proposal } from '@/api/types'
import { createEmptyProposalDraft, proposalToDraft } from './proposalDraftService'
import { outcomePresentation } from './proposalOutcomeService'

function proposal(overrides: Partial<Proposal> = {}): Proposal {
  return {
    id: 'proposal-1',
    engagementId: 'engagement-1',
    status: 'DRAFT',
    problemStatement: '',
    solutionStrategy: null,
    components: [],
    budget: '125000.5',
    timelineWeeks: 8,
    budgetConfidence: null,
    budgetSource: null,
    businessOutcomes: [],
    milestones: [],
    risks: [],
    assumptions: [],
    evidenceLinks: [],
    alignmentScore: 0,
    decision: 'PENDING',
    decisionRationale: '',
    clientResponse: null,
    clientDecisionOutcome: 'DEFERRED',
    decisionConfidence: 0,
    learnerPerformanceScore: 0,
    decisionDimensions: [],
    decisionInsights: [],
    evidenceImpacts: [],
    submittedAt: '',
    ...overrides,
  }
}

describe('proposal frontend/backend contract', () => {
  it('preserves textual money at the API boundary', () => {
    const request = { ...createEmptyProposalDraft(), budget: 125000.50 }

    expect(request.budget).toBe(125000.50)
    expect(typeof request.budget).toBe('number')
    expect(proposalToDraft(proposal()).budget).toBe(125000.5)
  })

  it('represents draft lifecycle fields with contract-safe values', () => {
    const unresolved = proposal()

    expect(unresolved.clientDecisionOutcome).toBe('DEFERRED')
    expect(unresolved.decisionRationale).toBe('')
    expect(unresolved.submittedAt).toBe('')
    expect(outcomePresentation(unresolved.clientDecisionOutcome).label).toBe('Decision deferred')
  })

  it('preserves actual resolved outcomes', () => {
    expect(outcomePresentation('REVISION_REQUESTED').label).toBe('Revision requested')
  })
})
