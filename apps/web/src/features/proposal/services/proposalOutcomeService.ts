import type { ClientDecisionOutcome, ProposalDecisionInsight } from '@/api/types'

export interface OutcomePresentation {
  label: string
  subtitle: string
  tagType: 'green' | 'blue' | 'purple' | 'warm-gray' | 'red'
  nextAction: string
}

const presentations: Record<ClientDecisionOutcome, OutcomePresentation> = {
  PILOT_APPROVED: {
    label: 'Pilot approved',
    subtitle: 'A controlled client pilot is approved, subject to the recorded conditions.',
    tagType: 'green',
    nextAction: 'Complete the learning assessment and capture the pilot conditions.',
  },
  PROPOSAL_ACCEPTED: {
    label: 'Proposal accepted',
    subtitle: 'The client accepted the recommendation and will progress its internal actions.',
    tagType: 'green',
    nextAction: 'Complete the learning assessment while the decision is fresh.',
  },
  STRATEGIC_PARTNERSHIP: {
    label: 'Strategic partnership',
    subtitle: 'The client supports a broader partnership beyond the initial engagement.',
    tagType: 'green',
    nextAction: 'Complete the learning assessment and review the conditions for expansion.',
  },
  REVISION_REQUESTED: {
    label: 'Revision requested',
    subtitle: 'The client sees potential but needs specific issues resolved before approval.',
    tagType: 'warm-gray',
    nextAction: 'Review the feedback and prepare a focused revision with stronger evidence.',
  },
  FURTHER_DISCOVERY_REQUIRED: {
    label: 'Further discovery required',
    subtitle: 'The client needs unanswered questions resolved before considering the proposal.',
    tagType: 'warm-gray',
    nextAction: 'Review the evidence gaps and take them into the next discovery cycle.',
  },
  DEFERRED: {
    label: 'Decision deferred',
    subtitle: 'The client is not ready to move forward under the current conditions.',
    tagType: 'warm-gray',
    nextAction: 'Use the assessment to identify the most material gaps before re-engaging.',
  },
  REJECTED: {
    label: 'Proposal rejected',
    subtitle: 'The proposal did not meet the client’s decision threshold in this engagement.',
    tagType: 'red',
    nextAction: 'Review the failure analysis and use the feedback in a future scenario.',
  },
}

export function outcomePresentation(outcome: ClientDecisionOutcome): OutcomePresentation {
  return presentations[outcome]
}

export function decisionInsights(insights: ProposalDecisionInsight[], category: ProposalDecisionInsight['category']) {
  return insights.filter((insight) => insight.category === category)
}
