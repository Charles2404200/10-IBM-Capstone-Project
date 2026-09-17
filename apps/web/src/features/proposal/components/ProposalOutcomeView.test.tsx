import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ProposalOutcomeView } from './ProposalOutcomeView'
import { useProposalCounterfactual, useProposalDecisionExplanation } from '@/api/hooks/useProposal'
import type { Proposal } from '@/api/types'
import styles from '@/pages/ProposalStudio/ProposalStudioPage.module.scss'

// mock the decision coaching hooks so tests can control their state
vi.mock('@/api/hooks/useProposal', () => ({
  useProposalDecisionExplanation: vi.fn(),
  useProposalCounterfactual: vi.fn(),
}))

// typed mock references
const mockedUseExplanation = vi.mocked(useProposalDecisionExplanation)
const mockedUseCounterfactual = vi.mocked(useProposalCounterfactual)

// creates an idle mutation result for the decision coaching hooks
function idleMutation() {
  return {
    data: undefined,
    isPending: false,
    mutate: vi.fn(),
  } as unknown as ReturnType<typeof useProposalDecisionExplanation>
}

// creates a base proposal object for tests with optional field overrides
function makeProposal(overrides: Partial<Proposal> = {}): Proposal {
  return {
    id: 'proposal-1',
    engagementId: 'engagement-1',
    status: 'SUBMITTED',
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

// renders the proposal outcome page with the expected router context
function renderOutcome(proposal: Proposal) {
  return render(
    <MemoryRouter>
      <ProposalOutcomeView proposal={proposal} engagementId="engagement-1" />
    </MemoryRouter>,
  )
}

describe('ProposalOutcomeView component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseExplanation.mockReturnValue(idleMutation())
    mockedUseCounterfactual.mockReturnValue(idleMutation())
  })

  it('does not render a decision rationale paragraph when decisionRationale is empty', () => {
    const { container } = renderOutcome(makeProposal({ decisionRationale: '' }))

    expect(container.querySelector(`.${styles.decisionRationale}`)).not.toBeInTheDocument()
    expect(screen.getByText('Strongest factor')).toBeInTheDocument()
  })

  it('renders the decision rationale paragraph when decisionRationale is present', () => {
    const { container } = renderOutcome(
      makeProposal({ decisionRationale: 'Strong evidence coverage across every dimension.' }),
    )

    expect(container.querySelector(`.${styles.decisionRationale}`),).toHaveTextContent('Strong evidence coverage across every dimension.')
  })

  it('falls back to clientResponse for "What the client said" when it is present', () => {
    renderOutcome(makeProposal({
      clientResponse: 'The client accepted the pilot proposal outright.',
      decisionRationale: 'Rationale text that should not be used here.',
    }))

    expect(screen.getByText('The client accepted the pilot proposal outright.')).toBeInTheDocument()
    expect(screen.getByText('Rationale text that should not be used here.')).toBeInTheDocument()
  })

  it('falls back to decisionRationale for "What the client said" when clientResponse is absent', () => {
    renderOutcome(makeProposal({
      clientResponse: null,
      decisionRationale: 'Rationale used because no client response exists.',
    }))

    // appears twice - once in the decision rail and once as the client-response fallback
    expect(screen.getAllByText('Rationale used because no client response exists.'),).toHaveLength(2)
  })

  // checks the default client response when both values are nullish
  it('shows the default message when clientResponse and decisionRationale are both nullish', () => {
    renderOutcome(makeProposal({
      clientResponse: null,
      decisionRationale: undefined as unknown as string,
    }))

    const clientResponseCard = screen.getByText('What the client said').closest('section')
    expect(clientResponseCard).toHaveTextContent('The client response is not yet available.')
  })

  // checks the empty client response when no client response or rationale is provided
  it('renders an empty client response section when decisionRationale is "" and clientResponse is absent', () => {
    renderOutcome(makeProposal({ clientResponse: null, decisionRationale: '' }))

    const clientResponseParagraphs = screen.getByText('What the client said').closest('section')?.querySelectorAll('p')

    expect(clientResponseParagraphs).toHaveLength(2)
    expect(clientResponseParagraphs?.[1]).toHaveTextContent('')
  })
})