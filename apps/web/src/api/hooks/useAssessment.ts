import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { Assessment } from '@/api/types'

export const assessmentKeys = {
  detail: (engagementId: string) => ['assessment', engagementId] as const,
}

export function useAssessment(engagementId: string) {
  return useQuery({
    queryKey: assessmentKeys.detail(engagementId),
    queryFn: async () => {
      const res = await apiClient.get<Assessment>(`/api/v1/engagements/${engagementId}/assessment`)
      return res.data
    },
    enabled: Boolean(engagementId),
    retry: false,
  })
}

export function useGenerateAssessment(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<Assessment>(`/api/v1/engagements/${engagementId}/assessment`)
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: assessmentKeys.detail(engagementId) }),
  })
}
