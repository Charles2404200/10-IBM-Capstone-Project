import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type {
  NotificationDetail,
  NotificationPage,
  UnreadNotificationCount,
} from '@/api/types'
import { NOTIFICATION_PAGE_SIZE } from '@/features/notifications/config'

export const notificationKeys = {
  all: ['notifications'] as const,
  pages: ['notifications', 'pages'] as const,
  detail: (eventId: string) => ['notifications', 'detail', eventId] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
}

export function useNotifications() {
  return useInfiniteQuery({
    queryKey: notificationKeys.pages,
    initialPageParam: null as string | null,
    queryFn: async ({ pageParam }) => {
      const response = await apiClient.get<NotificationPage>('/api/v1/notifications', {
        params: { limit: NOTIFICATION_PAGE_SIZE, cursor: pageParam ?? undefined },
      })
      return response.data
    },
    getNextPageParam: (lastPage) => lastPage.hasMore ? lastPage.nextCursor : undefined,
  })
}

export function useNotificationDetail(eventId: string | null) {
  return useQuery({
    queryKey: notificationKeys.detail(eventId ?? ''),
    enabled: Boolean(eventId),
    queryFn: async () => {
      const response = await apiClient.get<NotificationDetail>(
        `/api/v1/notifications/${encodeURIComponent(eventId!)}`,
      )
      return response.data
    },
  })
}

export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: notificationKeys.unreadCount,
    queryFn: async () => {
      const response = await apiClient.get<UnreadNotificationCount>('/api/v1/notifications/unread-count')
      return response.data
    },
    staleTime: 15_000,
  })
}

interface ReadMutationContext {
  // used for keeping the past successfull
  // cached memory data being stored
  // so we update the cached memory immediately
  // after sending the request
  // if the requests failed then we need to rollback
  // the cached memory so we just set the query data
  // back to the previous successfull fetch from
  // cache memory using the snapshot
  snapshots: Array<[QueryKey, unknown]>
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, string, ReadMutationContext>({
    mutationFn: async (eventId) => {
      await apiClient.patch(`/api/v1/notifications/${encodeURIComponent(eventId)}/read`)
    },
    onMutate: async (eventId) => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.all })
      const snapshots = queryClient.getQueriesData({ queryKey: notificationKeys.all })

      // these cached memory is updated for
      // responsiveness of the UI

      queryClient.setQueriesData<{ pages: NotificationPage[]; pageParams: unknown[] }>(
        { queryKey: notificationKeys.pages },
        (current) => current ? {
          ...current,
          pages: current.pages.map((page) => ({
            ...page,
            items: page.items.map((item) => item.eventId === eventId ? { ...item, isRead: true } : item),
          })),
        } : current,
      )
      queryClient.setQueryData<UnreadNotificationCount>(notificationKeys.unreadCount, (current) =>
        current ? { unreadCount: Math.max(0, current.unreadCount - 1) } : current,
      )
      queryClient.setQueryData<NotificationDetail>(notificationKeys.detail(eventId), (current) =>
        current ? { ...current, read: true } : current,
      )
      return { snapshots }
    },
    onError: (_error, _eventId, context) => {
      context?.snapshots.forEach(([key, value]) => queryClient.setQueryData(key, value))
    },
    onSettled: (_data, _error, eventId) => {
      // for correctness
      void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount })
      void queryClient.invalidateQueries({ queryKey: notificationKeys.detail(eventId) })
    },
  })
}
