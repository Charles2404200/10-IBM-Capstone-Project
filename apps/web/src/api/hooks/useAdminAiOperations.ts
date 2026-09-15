import { useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { AiOperationsResponse } from '@/api/types'

export function useAdminAiOperations(enabled = true) {
  return useQuery({
    queryKey: ['admin', 'ai', 'operations'],
    enabled,
    queryFn: async () => (await apiClient.get<AiOperationsResponse>('/api/v1/admin/ai/operations')).data,
    staleTime: 60_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: false,
  })
}
