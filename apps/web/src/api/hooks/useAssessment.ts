import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { Assessment } from '@/api/types'
import { engagementKeys } from '@/api/hooks/useEngagements'
import { portfolioKeys } from '@/api/hooks/usePortfolio'

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
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    refetchInterval: (query) => query.state.data?.coachingPending ? 1_500 : false,
  })
}

export function useGenerateAssessment(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<Assessment>(`/api/v1/engagements/${engagementId}/assessment`)
      return res.data
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: assessmentKeys.detail(engagementId) })
      void qc.invalidateQueries({ queryKey: engagementKeys.all })
      void qc.invalidateQueries({ queryKey: engagementKeys.detail(engagementId) })
      void qc.invalidateQueries({ queryKey: portfolioKeys.summary })
    },
  })
}
