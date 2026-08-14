import { describe, expect, it } from 'vitest'
import type { ResearchEvidence } from '@/api/types'
import { rankOutreachEvidence } from './outreachEvidence'

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
    sequenceNo: 1,
    supportingEvidenceIds: [],
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('rankOutreachEvidence', () => {
  it('puts verified and relevant evidence ahead of weaker signals', () => {
    const strongest = evidence({ id: 'strong', relevanceScore: 82, confidence: 'HIGH', verificationStatus: 'VERIFIED' })
    const weaker = evidence({ id: 'weak', relevanceScore: 75, confidence: 'LOW', verificationStatus: 'UNVERIFIED' })

    expect(rankOutreachEvidence([weaker, strongest]).map((item) => item.id)).toEqual(['strong', 'weak'])
  })

  it('never recommends hypotheses or contradicted evidence', () => {
    const valid = evidence({ id: 'valid' })
    const hypothesis = evidence({ id: 'hypothesis', evidenceType: 'HYPOTHESIS' })
    const contradicted = evidence({ id: 'contradicted', verificationStatus: 'CONTRADICTED' })

    expect(rankOutreachEvidence([hypothesis, contradicted, valid]).map((item) => item.id)).toEqual(['valid'])
  })
})
