import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { CapabilityBrief, OutreachAttempt } from '@/api/types'

export function useOutreach(engagementId: string) {
  return useQuery({
    queryKey: ['outreach', engagementId],
    queryFn: async () => {
      const res = await apiClient.get<OutreachAttempt[]>(
        `/api/v1/engagements/${engagementId}/outreach`
      )
      return res.data
    },
    enabled: Boolean(engagementId),
  })
}

export function useSendOutreach(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (data: { subject: string; body: string }) => {
      const res = await apiClient.post<OutreachAttempt>(
        `/api/v1/engagements/${engagementId}/outreach`,
        data
      )
      return res.data
    },
    onSuccess: (attempt) => {
      qc.setQueryData<OutreachAttempt[]>(['outreach', engagementId], (current = []) => [
        ...current.filter((item) => item.id !== attempt.id),
        attempt,
      ])
      void qc.invalidateQueries({ queryKey: ['outreach', engagementId] })
      void qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}

export function useCapabilityBrief(engagementId: string) {
  return useQuery({
    queryKey: ['capability-brief', engagementId],
    queryFn: async () => {
      const res = await apiClient.get<CapabilityBrief | null>(
        `/api/v1/engagements/${engagementId}/outreach/capability-brief`
      )
      return res.data
    },
    enabled: Boolean(engagementId),
  })
}

export function useSubmitCapabilityBrief(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (data: Pick<CapabilityBrief, 'relevantExperience' | 'approach' | 'caseExample' | 'clientFit'>) => {
      const res = await apiClient.post<CapabilityBrief>(
        `/api/v1/engagements/${engagementId}/outreach/capability-brief`, data
      )
      return res.data
    },
    onSuccess: (brief) => {
      qc.setQueryData(['capability-brief', engagementId], brief)
      void qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}
