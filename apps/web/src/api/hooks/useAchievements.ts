import { useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { AchievementSummary } from '@/api/types'

export function useMyAchievements() {
  return useQuery({
    queryKey: ['achievements', 'me'],
    queryFn: async () => {
      const res = await apiClient.get<AchievementSummary[]>('/api/v1/achievements/me')
      return res.data
    },
  })
}
