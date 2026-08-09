import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { EvidenceType, LeadIntelligence, LeadSummary, ResearchArtifact, ResearchEvidence, ResearchGateStatus, SaveResearchPayload } from '@/api/types'

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
    onSuccess: (savedEvidence) => {
      // The POST response is the authoritative newly-created row. Put it into
      // the active query immediately, then revalidate related server-derived
      // views in the background. This keeps the gate responsive without a
      // page refresh while preserving the backend as the source of truth.
      qc.setQueryData<ResearchEvidence[]>(['research', engagementId], (current = []) => {
        const withoutSavedRow = current.filter((item) => item.id !== savedEvidence.id)
        return [...withoutSavedRow, savedEvidence]
      })

      void qc.invalidateQueries({ queryKey: ['research', engagementId] })
      void qc.invalidateQueries({ queryKey: ['lead-intelligence', engagementId] })
      void qc.invalidateQueries({ queryKey: ['research-gate', engagementId] })
    },
  })
}

export function useGenerateResearchIntelligence(engagementId: string) {
  return useMutation({
    mutationFn: async (evidenceType: EvidenceType) => {
      const res = await apiClient.post<ResearchArtifact[]>(
        `/api/v1/engagements/${engagementId}/research-intelligence`,
        { evidenceType }
      )
      return res.data
    },
  })
}

export function useAnalyzeUserContext(engagementId: string) {
  return useMutation({
    mutationFn: async (context: string) => {
      const res = await apiClient.post<ResearchArtifact>(
        `/api/v1/engagements/${engagementId}/research-intelligence/user-context`,
        { context }
      )
      return res.data
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

/** Advances the engagement CLIENT_INTELLIGENCE -> HYPOTHESIS_READY once the gate
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
