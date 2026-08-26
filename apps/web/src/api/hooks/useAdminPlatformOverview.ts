import { useMutation, useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { AdminNotificationRequest, AdminNotificationResponse, PlatformOverview} from '@/api/types'

export const adminPlatformKeys = {
  overview: ['admin', 'platform', 'overview'] as const,
}

export function useAdminPlatformOverview(enabled: boolean) {
  return useQuery({
    queryKey: adminPlatformKeys.overview,
    enabled,
    queryFn: async () => (await apiClient.get<PlatformOverview>('/api/v1/admin/platform/overview')).data,
    staleTime: 20_000,
    refetchInterval: 30_000,
    refetchOnWindowFocus: false,
  })
}


export function useAdminNotifyUsers(
) {
  return useMutation({
    mutationFn: async (notificationObject: AdminNotificationRequest) => {
      const res = await apiClient.post<AdminNotificationResponse>(`/api/v1/admin/platform/publish-notifications`,
        notificationObject
      );
      return res.data;
    }
  });
}
