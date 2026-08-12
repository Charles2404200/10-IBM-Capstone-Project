import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import apiClient from '@/api/client'
import type { ScenarioCatalogPage, ScenarioSummary } from '@/api/types'

const SCENARIO_STALE_TIME = 10 * 60_000

export interface ScenarioCatalogFilters {
  search?: string
  industry?: string
  difficulty?: number
  page: number
  size: number
}

async function fetchScenarioCatalog(filters: ScenarioCatalogFilters) {
  const response = await apiClient.get<ScenarioCatalogPage>('/api/v1/scenarios/catalog', { params: filters })
  return response.data
}

export function useScenarioCatalog(filters: ScenarioCatalogFilters) {
  const queryClient = useQueryClient()
  const queryKey = ['scenario-catalog', filters] as const
  const query = useQuery({
    queryKey,
    queryFn: () => fetchScenarioCatalog(filters),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (!query.data || filters.page + 1 >= query.data.totalPages) return
    const nextFilters = { ...filters, page: filters.page + 1 }
    void queryClient.prefetchQuery({
      queryKey: ['scenario-catalog', nextFilters],
      queryFn: () => fetchScenarioCatalog(nextFilters),
      staleTime: 60_000,
    })
  }, [filters, query.data, queryClient])

  return query
}

export function useScenarioCatalogIndustries() {
  return useQuery({
    queryKey: ['scenario-catalog', 'industries'],
    queryFn: async () => (await apiClient.get<string[]>('/api/v1/scenarios/catalog/industries')).data,
    staleTime: 10 * 60_000,
  })
}

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
    staleTime: SCENARIO_STALE_TIME,
    refetchOnWindowFocus: false,
  })
}
