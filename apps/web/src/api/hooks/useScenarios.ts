import { useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { ScenarioSummary } from '@/api/types'

export function useScenarios() {
  return useQuery({
    queryKey: ['scenarios'],
    queryFn: async () => {
      const res = await apiClient.get<ScenarioSummary[]>('/api/v1/scenarios')
      return res.data
    },
  })
}

export function useScenario(id: string) {
  return useQuery({
    queryKey: ['scenarios', id],
    queryFn: async () => {
      const res = await apiClient.get<ScenarioSummary>(`/api/v1/scenarios/${id}`)
      return res.data
    },
    enabled: Boolean(id),
  })
}
