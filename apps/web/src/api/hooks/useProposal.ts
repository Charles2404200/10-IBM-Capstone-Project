import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { Proposal } from '@/api/types'

export const proposalKeys = {
  detail: (engagementId: string) => ['proposal', engagementId] as const,
}

export function useProposal(engagementId: string) {
  return useQuery({
    queryKey: proposalKeys.detail(engagementId),
    queryFn: async () => {
      const res = await apiClient.get<Proposal>(`/api/v1/engagements/${engagementId}/proposal`)
      return res.data
    },
    enabled: Boolean(engagementId),
    retry: false,
  })
}

export interface SubmitProposalRequest {
  problemStatement: string
  components: string[]
  budget: string
  timelineWeeks: number
}

export function useSubmitProposal(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (data: SubmitProposalRequest) => {
      const res = await apiClient.post<Proposal>(`/api/v1/engagements/${engagementId}/proposal`, data)
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: proposalKeys.detail(engagementId) })
      qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}
