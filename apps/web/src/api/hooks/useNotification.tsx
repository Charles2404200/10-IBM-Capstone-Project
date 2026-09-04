import { Client, type IMessage } from '@stomp/stompjs'
import { useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { z } from 'zod'
import type {
  NotificationObject,
  NotificationPage,
  UnreadNotificationCount,
  UserRole,
} from '@/api/types'
import { notificationKeys } from '@/api/hooks/useNotifications'
import {
  NOTIFICATION_BURST_WINDOW_MS,
  NOTIFICATION_DEDUPLICATION_CAPACITY,
  NOTIFICATION_PAGE_SIZE,
  NOTIFICATION_REFRESH_DEBOUNCE_MS,
} from '@/features/notifications/config'
import { addNotificationToPopupState, type NotificationPopupState } from '@/features/notifications/aggregation'
import { BoundedEventDeduplicator } from '@/features/notifications/deduplication'
import { notificationPreview } from '@/features/notifications/preview'
import { useAuthStore } from '@/store/authStore'

const realtimeSchema = z.object({
  eventId: z.string().uuid(),
  topicName: z.string().min(1).max(160),
  messagePreview: z.string().max(4000).optional(),
  message: z.string().max(4000).optional(),
  priority: z.enum(['NORMAL', 'IMPORTANT', 'CRITICAL']).default('NORMAL'),
  createdAt: z.string().datetime().optional(),
  role: z.enum(['LEARNER', 'SCENARIO_AUTHOR', 'REVIEWER', 'ADMINISTRATOR']),
})

interface NotificationRealtimeContextValue extends NotificationPopupState {
  dismissNotification: (eventId: string) => void
  clearPopups: () => void
}

const NotificationRealtimeContext = createContext<NotificationRealtimeContextValue | null>(null)

function toWebSocketUrl(baseUrl: string, role: string): string {
  const url = new URL(`/ws/notifications/${encodeURIComponent(role)}`, baseUrl || window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function toRoleSlug(role: string): string {
  return role.toLowerCase().replaceAll('_', '-')
}

function parseRealtimeNotification(message: IMessage): NotificationObject | null {
  try {
    const parsed = realtimeSchema.safeParse(JSON.parse(message.body))
    if (!parsed.success) return null
    const value = parsed.data
    const preview = notificationPreview(value.messagePreview ?? value.message ?? '')
    return {
      eventId: value.eventId,
      topicName: value.topicName,
      messagePreview: preview,
      message: preview,
      role: value.role as UserRole,
      priority: value.priority,
      createdAt: value.createdAt ?? new Date().toISOString(),
    }
  } catch {
    return null
  }
}

export function NotificationRealtimeProvider({ children }: { children: ReactNode }) {
  const [popups, setPopups] = useState<NotificationPopupState>({ visible: [], overflowCount: 0 })
  const deduplicator = useRef(new BoundedEventDeduplicator(NOTIFICATION_DEDUPLICATION_CAPACITY))
  const refreshTimer = useRef<number | null>(null)
  const burstStartedAt = useRef(0)
  const queryClient = useQueryClient()
  const token = useAuthStore((state) => state.token)
  const role = useAuthStore((state) => state.role)
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

  const refreshDurableState = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: notificationKeys.pages })
    void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount })
  }, [queryClient])

  const scheduleRefresh = useCallback(() => {
    if (refreshTimer.current !== null) return
    refreshTimer.current = window.setTimeout(() => {
      refreshTimer.current = null
      refreshDurableState()
    }, Math.min(NOTIFICATION_REFRESH_DEBOUNCE_MS, NOTIFICATION_BURST_WINDOW_MS))
  }, [refreshDurableState])

  const rememberEvent = useCallback((eventId: string) => {
    return deduplicator.current.remember(eventId)
  }, [])

  const applyRealtimeHint = useCallback((notification: NotificationObject) => {
    // Realtime is an at-least-once hint. Update already-loaded bounded caches
    // immediately, then reconcile with REST because PostgreSQL is authoritative.
    let alreadyInLoadedPages = false
    queryClient.setQueryData<InfiniteData<NotificationPage, string | null>>(
      notificationKeys.pages,
      (current) => {
        if (!current) return current
        if (current.pages.some((page) =>
          page.items.some((item) => item.eventId === notification.eventId))) {
          alreadyInLoadedPages = true
          return current
        }
        const [first, ...remaining] = current.pages
        // if the page is empty then first element is empty
        if (!first) return current

        // only updating the first page
        return {
          ...current,
          pages: [{
            ...first,
            items: [{
              eventId: notification.eventId,
              topicName: notification.topicName,
              messagePreview: notification.messagePreview,
              priority: notification.priority,
              createdAt: notification.createdAt,
              isRead: false,
            }, ...first.items].slice(0, NOTIFICATION_PAGE_SIZE),
          }, ...remaining],
        }
      },
    )
    if (!alreadyInLoadedPages) {
      queryClient.setQueryData<UnreadNotificationCount>(notificationKeys.unreadCount, (current) =>
        current ? { unreadCount: current.unreadCount + 1 } : current,
      )
    }
  }, [queryClient])

  useEffect(() => {
    if (!token || !role) return
    const roleSlug = toRoleSlug(role)
    const client = new Client({
      brokerURL: toWebSocketUrl(baseUrl, roleSlug),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    })

    client.onConnect = () => {
      refreshDurableState()
      client.subscribe(`/topic/notifications/${roleSlug}`, (message) => {
        const notification = parseRealtimeNotification(message)
        if (!notification || notification.role !== role || !rememberEvent(notification.eventId)) return

        const now = Date.now()
        const startsNewBurst = now - burstStartedAt.current > NOTIFICATION_BURST_WINDOW_MS
        if (startsNewBurst) {
          burstStartedAt.current = now
        }
        setPopups((current) => addNotificationToPopupState(
          startsNewBurst ? { visible: [], overflowCount: 0 } : current,
          notification,
        ))
        applyRealtimeHint(notification)
        scheduleRefresh()
      })
    }

    const reconcileWhenActive = () => {
      if (document.visibilityState !== 'hidden') refreshDurableState()
    }
    // suppose when we select or come back to the browser
    // not a tab but browser then the application
    // invalidates react query cache memory and pulls the latest
    // data from the database and if the tab is not
    // the tab where this application is running
    // then do not refetch the data
    window.addEventListener('focus', reconcileWhenActive)
    // suppose when the tab changes and then if the tab is not current visible
    // to the user then do not refetch the data and if the tab is visible
    // to the user then invalidate the cache memory of react query and
    // refetch it from the database
    document.addEventListener('visibilitychange', reconcileWhenActive)
    client.activate()
    return () => {
      window.removeEventListener('focus', reconcileWhenActive)
      document.removeEventListener('visibilitychange', reconcileWhenActive)
      void client.deactivate()
      if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current)
      refreshTimer.current = null
    }
  }, [applyRealtimeHint, baseUrl, refreshDurableState, rememberEvent, role, scheduleRefresh, token])

  const dismissNotification = useCallback((eventId: string) => {
    setPopups((current) => ({
      ...current,
      visible: current.visible.filter((item) => item.eventId !== eventId),
    }))
  }, [])

  const clearPopups = useCallback(() => {
    setPopups({ visible: [], overflowCount: 0 })
  }, [])

  const value = useMemo(() => ({ ...popups, dismissNotification, clearPopups }), [
    clearPopups,
    dismissNotification,
    popups,
  ])
  return <NotificationRealtimeContext.Provider value={value}>{children}</NotificationRealtimeContext.Provider>
}

// Provider and its consumer hook intentionally share the private context contract.
// eslint-disable-next-line react-refresh/only-export-components
export function useNotification() {
  const context = useContext(NotificationRealtimeContext)
  if (!context) throw new Error('useNotification must be used within NotificationRealtimeProvider')
  return context
}
