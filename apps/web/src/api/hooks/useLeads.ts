import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { LeadIntelligence, LeadSummary, ResearchEvidence, ResearchGateStatus, SaveResearchPayload } from '@/api/types'

export function useLeads(scenarioId: string) {
  return useQuery({
    queryKey: ['leads', scenarioId],
    queryFn: async () => {
      const res = await apiClient.get<LeadSummary[]>(`/api/v1/scenarios/${scenarioId}/leads`)
      return res.data
    },
    enabled: Boolean(scenarioId),
  })
}

export function useSelectLead(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (leadId: string) => {
      await apiClient.post(`/api/v1/engagements/${engagementId}/lead-selection`, { leadId })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['engagements', engagementId] }),
  })
}

export function useResearch(engagementId: string) {
  return useQuery({
    queryKey: ['research', engagementId],
    queryFn: async () => {
      const res = await apiClient.get<ResearchEvidence[]>(
        `/api/v1/engagements/${engagementId}/research`
      )
      return res.data
    },
    enabled: Boolean(engagementId),
  })
}

export function useSaveResearch(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (data: SaveResearchPayload) => {
      const res = await apiClient.post<ResearchEvidence>(
        `/api/v1/engagements/${engagementId}/research`,
        data
      )
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['research', engagementId] })
      qc.invalidateQueries({ queryKey: ['lead-intelligence', engagementId] })
    },
  })
}

/** Powers the Client Intelligence "Client Profile" panel — hidden fields revealed
 *  progressively as research evidence accumulates. */
export function useLeadIntelligence(engagementId: string) {
  return useQuery({
    queryKey: ['lead-intelligence', engagementId],
    queryFn: async () => {
      const res = await apiClient.get<LeadIntelligence>(
        `/api/v1/engagements/${engagementId}/lead-intelligence`
      )
      return res.data
    },
    enabled: Boolean(engagementId),
  })
}

/** The "Proceed to Outreach" requirements checklist — polled so the gate UI
 *  stays in sync as the learner adds evidence. */
export function useResearchGateStatus(engagementId: string) {
  return useQuery({
    queryKey: ['research-gate', engagementId],
    queryFn: async () => {
      const res = await apiClient.get<ResearchGateStatus>(
        `/api/v1/engagements/${engagementId}/research-readiness`
      )
      return res.data
    },
    enabled: Boolean(engagementId),
  })
}

/** Advances the engagement LEAD_SELECTED → RESEARCH_COMPLETED once the gate
 *  conditions are met, unlocking Outreach. Rejected with 422 (surfaced via
 *  mutation.error) if the learner hasn't satisfied the requirements yet. */
export function useCompleteResearch(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<ResearchGateStatus>(
        `/api/v1/engagements/${engagementId}/research/complete`
      )
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['research-gate', engagementId] })
      qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}
