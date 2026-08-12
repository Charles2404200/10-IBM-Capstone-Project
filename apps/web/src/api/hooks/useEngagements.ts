import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { Engagement } from '@/api/types'

export const engagementKeys = {
  all: ['engagements'] as const,
  detail: (id: string) => ['engagements', id] as const,
}

export function useMyEngagements() {
  return useQuery({
    queryKey: engagementKeys.all,
    queryFn: async () => {
      const res = await apiClient.get<Engagement[]>('/api/v1/engagements')
      return res.data
    },
  })
}

export function useEngagement(id: string) {
  return useQuery({
    queryKey: engagementKeys.detail(id),
    queryFn: async () => {
      const res = await apiClient.get<Engagement>(`/api/v1/engagements/${id}`)
      return res.data
    },
    enabled: Boolean(id),
  })
}

export function useStartEngagement() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (params: { scenarioId: string; personaId?: string }) => {
      const res = await apiClient.post<Engagement>('/api/v1/engagements', params)
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: engagementKeys.all }),
  })
}

/** Starts directly from a server-catalogued lead; selection is performed atomically by the backend. */
export function useStartEngagementFromLead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (params: { leadId: string; personaId?: string }) => {
      const res = await apiClient.post<Engagement>('/api/v1/engagements/from-lead', params)
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: engagementKeys.all }),
  })
}

export function useRetryEngagement(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<Engagement>(`/api/v1/engagements/${engagementId}/retry`)
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: engagementKeys.all }),
  })
}
