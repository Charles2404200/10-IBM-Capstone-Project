import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import apiClient from '@/api/client'
import type { AdminUserPage, AdminUserSummary, UserRole } from '@/api/types'

const ADMIN_USER_DIRECTORY_STALE_TIME = 60_000
const adminUserKeys = {
  all: ['admin', 'users'] as const,
  directory: (filters: AdminUserDirectoryFilters) => ['admin', 'users', 'directory', filters] as const,
}

export interface AdminUserDirectoryFilters {
  search?: string
  role?: UserRole
  active?: boolean
  page: number
  size: number
}

async function fetchAdminUserDirectory(filters: AdminUserDirectoryFilters) {
  return (await apiClient.get<AdminUserPage>('/api/v1/admin/users', { params: filters })).data
}

export function useAdminUsers(filters: AdminUserDirectoryFilters) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: adminUserKeys.directory(filters),
    queryFn: () => fetchAdminUserDirectory(filters),
    placeholderData: keepPreviousData,
    staleTime: ADMIN_USER_DIRECTORY_STALE_TIME,
    gcTime: 10 * 60_000,
    refetchOnWindowFocus: false,
  })

  useEffect(() => {
    if (!query.data || filters.page + 1 >= query.data.totalPages) return
    const nextFilters = { ...filters, page: filters.page + 1 }
    void queryClient.prefetchQuery({
      queryKey: adminUserKeys.directory(nextFilters),
      queryFn: () => fetchAdminUserDirectory(nextFilters),
      staleTime: ADMIN_USER_DIRECTORY_STALE_TIME,
    })
  }, [filters, query.data, queryClient])

  return query
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
