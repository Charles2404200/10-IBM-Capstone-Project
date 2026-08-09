import type { Proposal, ProposalSource } from '@/api/types'
import type { ProposalDraftRequest } from '@/api/hooks/useProposal'

export type ProposalSection = 'PROBLEM' | 'OUTCOMES' | 'TIMELINE' | 'RISKS' | 'ASSUMPTIONS'

export const proposalSections: { id: ProposalSection; label: string }[] = [
  { id: 'PROBLEM', label: 'Foundation' },
  { id: 'OUTCOMES', label: 'Value & commercial' },
  { id: 'TIMELINE', label: 'Delivery plan' },
  { id: 'RISKS', label: 'Risks & assumptions' },
  { id: 'ASSUMPTIONS', label: 'Evidence & review' },
]

export function createEmptyProposalDraft(): ProposalDraftRequest {
  return {
    problemStatement: '',
    solutionStrategy: '',
    components: [''],
    budget: '0',
    timelineWeeks: 8,
    budgetConfidence: 'UNCONFIRMED',
    budgetSource: 'Consultant estimate',
    businessOutcomes: [{ outcome: '', metric: '', target: '' }],
    milestones: [{ phase: '', duration: '' }],
    risks: [{ risk: '', severity: 'MEDIUM', mitigation: '' }],
    assumptions: [''],
    evidenceLinks: [],
  }
}

export function proposalToDraft(proposal: Proposal): ProposalDraftRequest {
  return {
    problemStatement: proposal.problemStatement ?? '',
    solutionStrategy: proposal.solutionStrategy ?? '',
    components: proposal.components.length ? proposal.components : [''],
    budget: proposal.budget ?? '0',
    timelineWeeks: proposal.timelineWeeks || 8,
    budgetConfidence: proposal.budgetConfidence ?? 'UNCONFIRMED',
    budgetSource: proposal.budgetSource ?? 'Consultant estimate',
    businessOutcomes: proposal.businessOutcomes.length ? proposal.businessOutcomes : [{ outcome: '', metric: '', target: '' }],
    milestones: proposal.milestones.length ? proposal.milestones : [{ phase: '', duration: '' }],
    risks: proposal.risks.length ? proposal.risks : [{ risk: '', severity: 'MEDIUM', mitigation: '' }],
    assumptions: proposal.assumptions.length ? proposal.assumptions : [''],
    evidenceLinks: proposal.evidenceLinks ?? [],
  }
}

export function isSourceAttached(draft: ProposalDraftRequest, section: ProposalSection, sourceId: string) {
  return draft.evidenceLinks.some((link) => link.section === section && link.sourceId === sourceId)
}

export function attachSource(draft: ProposalDraftRequest, section: ProposalSection, source: ProposalSource) {
  if (isSourceAttached(draft, section, source.id)) return draft
  return { ...draft, evidenceLinks: [...draft.evidenceLinks, { section, sourceId: source.id }] }
}

export function detachSource(draft: ProposalDraftRequest, section: ProposalSection, sourceId: string) {
  return {
    ...draft,
    evidenceLinks: draft.evidenceLinks.filter((link) => !(link.section === section && link.sourceId === sourceId)),
  }
}
