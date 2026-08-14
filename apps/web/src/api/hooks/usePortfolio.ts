import { useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { PortfolioSummary, ReplayComparison } from '@/api/types'

export const portfolioKeys = {
  summary: ['portfolio', 'summary'] as const,
  replay: (a: string, b: string) => ['portfolio', 'replay', a, b] as const,
}

export function usePortfolioSummary() {
  return useQuery({
    queryKey: portfolioKeys.summary,
    queryFn: async () => {
      const res = await apiClient.get<PortfolioSummary>('/api/v1/portfolio/summary')
      return res.data
    },
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  })
}

export function useReplayComparison(engagementA: string, engagementB: string) {
  return useQuery({
    queryKey: portfolioKeys.replay(engagementA, engagementB),
    queryFn: async () => {
      const res = await apiClient.get<ReplayComparison>('/api/v1/portfolio/replay', {
        params: { engagementA, engagementB },
      })
      return res.data
    },
    enabled: Boolean(engagementA) && Boolean(engagementB) && engagementA !== engagementB,
  })
}
