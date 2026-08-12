import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { AdminUserSummary, UserRole } from '@/api/types'

const adminUserKeys = { all: ['admin', 'users'] as const }

export function useAdminUsers() {
  return useQuery({
    queryKey: adminUserKeys.all,
    queryFn: async () => (await apiClient.get<AdminUserSummary[]>('/api/v1/admin/users')).data,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  })
}

export function useChangeUserRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ userId, role }: { userId: string; role: UserRole }) =>
      (await apiClient.patch<AdminUserSummary>(`/api/v1/admin/users/${userId}/role`, { role })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminUserKeys.all }),
  })
}

export function useSetUserActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ userId, active }: { userId: string; active: boolean }) =>
      (await apiClient.patch<AdminUserSummary>(`/api/v1/admin/users/${userId}/${active ? 'reactivate' : 'deactivate'}`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminUserKeys.all }),
  })
}
