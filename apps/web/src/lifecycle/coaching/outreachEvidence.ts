import type { ResearchEvidence } from '@/api/types'

const confidenceWeight = { HIGH: 24, MEDIUM: 12, LOW: 0 } as const
const verificationWeight = {
  VERIFIED: 20,
  CORROBORATED: 12,
  UNVERIFIED: -6,
  CONTRADICTED: -40,
} as const
const originWeight = {
  MEETING_DISCOVERY: 8,
  SCENARIO_CURATED: 6,
  AI_SYNTHESIZED: 2,
  USER_SUPPLIED: 0,
} as const

/**
 * Ranks existing, traceable research for the outreach workspace. This is a
 * recommendation only: it never changes the evidence stored on an engagement.
 */
export function rankOutreachEvidence(evidence: ResearchEvidence[]): ResearchEvidence[] {
  return [...evidence]
    .filter((item) => item.evidenceType !== 'HYPOTHESIS' && item.verificationStatus !== 'CONTRADICTED')
    .sort((left, right) => outreachEvidenceScore(right) - outreachEvidenceScore(left) || right.sequenceNo - left.sequenceNo)
}

function outreachEvidenceScore(item: ResearchEvidence): number {
  return item.relevanceScore
    + confidenceWeight[item.confidence]
    + verificationWeight[item.verificationStatus]
    + originWeight[item.origin]
}
