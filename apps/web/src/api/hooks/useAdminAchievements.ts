import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { AchievementAdminView, UpsertAchievementRequest } from '@/api/types'

const adminAchievementKeys = {
  all: ['admin', 'achievements'] as const,
}

export function useAdminAchievements() {
  return useQuery({
    queryKey: adminAchievementKeys.all,
    queryFn: async () => {
      const res = await apiClient.get<AchievementAdminView[]>('/api/v1/admin/achievements')
      return res.data
    },
  })
}

export function useCreateAchievement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: UpsertAchievementRequest) => {
      const res = await apiClient.post<AchievementAdminView>('/api/v1/admin/achievements', request)
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAchievementKeys.all }),
  })
}

export function useUpdateAchievement(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: UpsertAchievementRequest) => {
      const res = await apiClient.put<AchievementAdminView>(`/api/v1/admin/achievements/${id}`, request)
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAchievementKeys.all }),
  })
}

export function useSetAchievementActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      const res = await apiClient.patch<AchievementAdminView>(
        `/api/v1/admin/achievements/${id}/${active ? 'activate' : 'deactivate'}`,
      )
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAchievementKeys.all }),
  })
}
