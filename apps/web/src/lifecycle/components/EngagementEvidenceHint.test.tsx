import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { useResearch } from '@/api/hooks/useLeads'
import type { ResearchEvidence } from '@/api/types'
import EngagementEvidenceHint from './EngagementEvidenceHint'

vi.mock('@/api/hooks/useLeads', () => ({ useResearch: vi.fn() }))

const mockedUseResearch = vi.mocked(useResearch)

function evidence(overrides: Partial<ResearchEvidence>): ResearchEvidence {
  return {
    id: crypto.randomUUID(),
    engagementId: 'engagement-1',
    note: 'A traceable client signal.',
    hypothesis: null,
    evidenceType: 'COMPANY_NEWS',
    sourceUrl: null,
    sourceTitle: 'Client announcement',
    origin: 'SCENARIO_CURATED',
    verificationStatus: 'VERIFIED',
    occurredOn: null,
    confidence: 'MEDIUM',
    relevanceScore: 50,
    reasoningLane: null,
    sequenceNo: 1,
    supportingEvidenceIds: [],
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('EngagementEvidenceHint', () => {
  it('separates the identified problem from usable evidence and excludes contradicted signals', async () => {
    mockedUseResearch.mockReturnValue({
      data: [
        evidence({ id: 'evidence-1', note: 'Guest satisfaction varies across properties.', sequenceNo: 1 }),
        evidence({ id: 'problem-1', evidenceType: 'HYPOTHESIS', hypothesis: 'Inconsistent operating practices are damaging the guest experience.', supportingEvidenceIds: ['evidence-1'], sequenceNo: 2 }),
        evidence({ id: 'contradicted-1', note: 'This signal was contradicted.', verificationStatus: 'CONTRADICTED', sequenceNo: 3 }),
      ],
      isLoading: false,
    } as unknown as ReturnType<typeof useResearch>)

    render(<EngagementEvidenceHint engagementId="engagement-1" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Open engagement evidence' }))

    expect(screen.getByText('Problem identified')).toBeInTheDocument()
    expect(screen.getByText('Inconsistent operating practices are damaging the guest experience.')).toBeInTheDocument()
    expect(screen.getByText('Guest satisfaction varies across properties.')).toBeInTheDocument()
    expect(screen.queryByText('This signal was contradicted.')).not.toBeInTheDocument()
    expect(screen.getByText('1 usable')).toBeInTheDocument()
  })
})