import { useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { PlatformOverview } from '@/api/types'

export function useAdminPlatformOverview(enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'platform', 'overview'],
    enabled,
    queryFn: async () => (await apiClient.get<PlatformOverview>('/api/v1/admin/platform/overview')).data,
  })
}
