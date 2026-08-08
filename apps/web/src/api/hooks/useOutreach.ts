import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { OutreachAttempt } from '@/api/types'

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
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['outreach', engagementId] })
      qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}
