import { describe, expect, it } from 'vitest'
import type { Proposal } from '@/api/types'
import { toProposalDraftRequest } from '@/api/hooks/useProposal'
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
    budget: 125000.5,
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
    decisionRationale: null,
    clientResponse: null,
    clientDecisionOutcome: null,
    decisionConfidence: 0,
    learnerPerformanceScore: 0,
    decisionDimensions: [],
    decisionInsights: [],
    evidenceImpacts: [],
    submittedAt: null,
    ...overrides,
  }
}

describe('proposal frontend/backend contract', () => {
  it('converts textual form money to a JSON number at the API boundary', () => {
    const request = toProposalDraftRequest({ ...createEmptyProposalDraft(), budget: '125000.50' })

    expect(request.budget).toBe(125000.5)
    expect(typeof request.budget).toBe('number')
    expect(proposalToDraft(proposal()).budget).toBe('125000.5')
  })

  it.each(['', 'not-a-number', '-1', 'Infinity'])('rejects invalid budget form value %j', (budget) => {
    expect(() => toProposalDraftRequest({ ...createEmptyProposalDraft(), budget }))
      .toThrow('Budget must be a non-negative number')
  })

  it('rejects invalid timeline values before making an API call', () => {
    expect(() => toProposalDraftRequest({ ...createEmptyProposalDraft(), timelineWeeks: 0 }))
      .toThrow('Timeline must be a positive whole number of weeks')
  })

  it('represents unresolved proposal fields and presentation explicitly', () => {
    const unresolved = proposal()

    expect(unresolved.clientDecisionOutcome).toBeNull()
    expect(unresolved.decisionRationale).toBeNull()
    expect(unresolved.submittedAt).toBeNull()
    expect(outcomePresentation(unresolved.clientDecisionOutcome).label).toBe('Decision pending')
  })

  it('preserves actual resolved outcomes', () => {
    expect(outcomePresentation('REVISION_REQUESTED').label).toBe('Revision requested')
  })
})
