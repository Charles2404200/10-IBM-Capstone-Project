import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { Engagement, LeadSummary, ScenarioSummary } from '@/api/types'

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
    staleTime: ENGAGEMENT_STALE_TIME,
    refetchOnWindowFocus: false,
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
    staleTime: ENGAGEMENT_STALE_TIME,
    refetchOnWindowFocus: false,
  })
}

export function useStartEngagement() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (params: { scenarioId: string; personaId?: string; scenario?: ScenarioSummary }) => {
      const { scenario, ...request } = params
      const res = await apiClient.post<Engagement>('/api/v1/engagements', request)
      return { engagement: res.data, scenario }
    },
    onSuccess: ({ engagement, scenario }) => {
      cacheStartedEngagement(qc, engagement, scenario)
      if (scenario) {
        void qc.prefetchQuery({
          queryKey: ['leads', scenario.id],
          queryFn: async () => (await apiClient.get<LeadSummary[]>(`/api/v1/scenarios/${scenario.id}/leads`)).data,
          staleTime: 10 * 60_000,
        })
      }
    },
  })
}

const ENGAGEMENT_STALE_TIME = 30_000

function cacheStartedEngagement(
  queryClient: ReturnType<typeof useQueryClient>,
  engagement: Engagement,
  scenario?: ScenarioSummary,
) {
  const cachedEngagement: Engagement = {
    ...engagement,
    scenarioTitle: scenario?.title ?? engagement.scenarioTitle,
    scenarioIndustry: scenario?.industry ?? engagement.scenarioIndustry,
  }
  queryClient.setQueryData<Engagement>(engagementKeys.detail(engagement.id), cachedEngagement)
  if (scenario) {
    queryClient.setQueryData<ScenarioSummary>(['scenarios', scenario.id], scenario)
  }
  queryClient.setQueryData<Engagement[]>(engagementKeys.all, (current = []) => [
    cachedEngagement,
    ...current.filter((item) => item.id !== engagement.id),
  ])
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
