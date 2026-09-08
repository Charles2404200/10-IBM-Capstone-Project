import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type {
  Proposal,
  ProposalChallenge,
  ProposalDecisionExplanation,
  ProposalReview,
  ProposalWorkspace,
} from '@/api/types'

export const proposalKeys = {
  detail: (engagementId: string) => ['proposal', engagementId] as const,
  workspace: (engagementId: string) => ['proposal-workspace', engagementId] as const,
}

export interface ProposalDraftRequest {
  problemStatement: string
  solutionStrategy: string
  components: string[]
  budget: number
  timelineWeeks: number
  budgetConfidence: string
  budgetSource: string
  businessOutcomes: { outcome: string; metric: string; target: string }[]
  milestones: { phase: string; duration: string }[]
  risks: { risk: string; severity: string; mitigation: string }[]
  assumptions: string[]
  evidenceLinks: { section: string; sourceId: string }[]
}

/** Editable form state remains textual until it is validated at the HTTP boundary. */
export type ProposalDraftForm = Omit<ProposalDraftRequest, 'budget'> & { budget: string }

export function toProposalDraftRequest(draft: ProposalDraftForm): ProposalDraftRequest {
  const budget = draft.budget.trim() === '' ? Number.NaN : Number(draft.budget)
  if (!Number.isFinite(budget) || budget < 0) {
    throw new Error('Budget must be a non-negative number')
  }
  if (!Number.isInteger(draft.timelineWeeks) || draft.timelineWeeks <= 0) {
    throw new Error('Timeline must be a positive whole number of weeks')
  }
  return { ...draft, budget }
}

export function useProposal(engagementId: string) {
  return useQuery({
    queryKey: proposalKeys.detail(engagementId),
    queryFn: async () => (await apiClient.get<Proposal>(`/api/v1/engagements/${engagementId}/proposal`)).data,
    enabled: Boolean(engagementId),
    retry: false,
  })
}

export function useProposalWorkspace(engagementId: string) {
  return useQuery({
    queryKey: proposalKeys.workspace(engagementId),
    queryFn: async () => (await apiClient.get<ProposalWorkspace>(`/api/v1/engagements/${engagementId}/proposal/workspace`)).data,
    enabled: Boolean(engagementId),
  })
}

export function useSaveProposalDraft(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (draft: ProposalDraftForm) =>
      (await apiClient.put<Proposal>(`/api/v1/engagements/${engagementId}/proposal/draft`,
        toProposalDraftRequest(draft))).data,
    onSuccess: (proposal) => {
      qc.setQueryData(proposalKeys.detail(engagementId), proposal)
      qc.setQueryData<ProposalWorkspace | undefined>(proposalKeys.workspace(engagementId), (current) =>
        current ? { ...current, proposal } : current
      )
      void qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}

export function useProposalReview(engagementId: string) {
  return useMutation({
    mutationFn: async (draft: ProposalDraftForm) =>
      (await apiClient.post<ProposalReview>(`/api/v1/engagements/${engagementId}/proposal/review`,
        toProposalDraftRequest(draft))).data,
  })
}

export function useProposalChallenge(engagementId: string) {
  return useMutation({
    mutationFn: async (draft: ProposalDraftForm) =>
      (await apiClient.post<ProposalChallenge>(`/api/v1/engagements/${engagementId}/proposal/challenge`,
        toProposalDraftRequest(draft))).data,
  })
}

export function useSubmitProposal(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (draft: ProposalDraftForm) =>
      (await apiClient.post<Proposal>(`/api/v1/engagements/${engagementId}/proposal`,
        toProposalDraftRequest(draft))).data,
    onSuccess: (proposal) => {
      qc.setQueryData(proposalKeys.detail(engagementId), proposal)
      qc.setQueryData<ProposalWorkspace | undefined>(proposalKeys.workspace(engagementId), (current) =>
        current ? { ...current, proposal } : current
      )
      void qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}

function useDecisionNarrative(engagementId: string, endpoint: 'explanation' | 'counterfactual') {
  return useMutation({
    mutationFn: async () =>
      (await apiClient.post<ProposalDecisionExplanation>(
        `/api/v1/engagements/${engagementId}/proposal/decision/${endpoint}`,
      )).data,
  })
}

export function useProposalDecisionExplanation(engagementId: string) {
  return useDecisionNarrative(engagementId, 'explanation')
}

export function useProposalCounterfactual(engagementId: string) {
  return useDecisionNarrative(engagementId, 'counterfactual')
}
