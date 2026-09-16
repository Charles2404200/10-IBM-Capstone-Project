import { Client, ReconnectionTimeMode, type IFrame, type IMessage } from '@stomp/stompjs'
import { useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { z } from 'zod'
import type {
  NotificationObject,
  NotificationPage,
  UnreadNotificationCount,
  UserRole,
} from '@/api/types'
import { invalidateCurrentSession } from '@/api/authSession'
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
  connectionState: NotificationConnectionState
  dismissNotification: (eventId: string) => void
  clearPopups: () => void
}

export type NotificationConnectionState = 'connecting' | 'connected' | 'reconnecting'

const INITIAL_RECONNECT_DELAY_MS = 1_000
const MAX_RECONNECT_DELAY_MS = 30_000
const AUTHENTICATION_FAILURE_MESSAGES = [
  'invalid or missing authentication token',
  'unknown or inactive user',
  'unauthenticated notification connection',
  'not authorized for this notification endpoint',
]

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

function isAuthenticationFailure(frame: IFrame): boolean {
  const details = `${frame.headers.message ?? ''} ${frame.body ?? ''}`.toLowerCase()
  return AUTHENTICATION_FAILURE_MESSAGES.some((message) => details.includes(message))
}

function deactivateClient(client: Client): Promise<void> {
  // Graceful shutdown is preferred. Force-close only if STOMP.js cannot finish
  // it, so a failed teardown cannot block all future notification connections.
  return client.deactivate().catch(() => client.deactivate({ force: true }))
}

export function NotificationRealtimeProvider({ children }: { children: ReactNode }) {
  const [popups, setPopups] = useState<NotificationPopupState>({ visible: [], overflowCount: 0 })
  const [connectionState, setConnectionState] = useState<NotificationConnectionState>('connecting')
  const deduplicator = useRef(new BoundedEventDeduplicator(NOTIFICATION_DEDUPLICATION_CAPACITY))
  const refreshTimer = useRef<number | null>(null)
  const burstStartedAt = useRef(0)
  const activeClient = useRef<Client | null>(null)
  const previousDeactivation = useRef<Promise<void>>(Promise.resolve())
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
    setConnectionState('connecting')
    const roleSlug = toRoleSlug(role)
    const client = new Client({
      brokerURL: toWebSocketUrl(baseUrl, roleSlug),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    })
    let disposed = false
    let terminalAuthenticationFailure = false
    activeClient.current = client

    const isCurrentClient = () => !disposed && activeClient.current === client
    const markReconnecting = () => {
      if (isCurrentClient() && !terminalAuthenticationFailure) {
        setConnectionState('reconnecting')
      }
    }

    client.onConnect = () => {
      if (!isCurrentClient() || terminalAuthenticationFailure) return
      setConnectionState('connected')
      refreshDurableState()
      client.subscribe(`/topic/notifications/${roleSlug}`, (message) => {
        if (!isCurrentClient() || terminalAuthenticationFailure) return
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

    // A close is the authoritative transport failure signal, including the
    // close STOMP.js performs after a heartbeat timeout. STOMP.js remains the
    // sole owner of scheduling the subsequent reconnect.
    client.onWebSocketClose = markReconnecting
    client.onWebSocketError = markReconnecting
    client.onStompError = (frame) => {
      if (!isCurrentClient()) return
      if (!isAuthenticationFailure(frame)) {
        markReconnecting()
        return
      }

      terminalAuthenticationFailure = true
      activeClient.current = null
      // Deactivation immediately disables STOMP.js's retry loop. The token
      // match inside invalidateCurrentSession prevents a stale socket failure
      // from logging out a newer session.
      previousDeactivation.current = deactivateClient(client)
      invalidateCurrentSession(token)
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
    // React Strict Mode and token/role changes can start a new effect while the
    // previous client's asynchronous shutdown is still completing. Serialize
    // activation behind that shutdown so only one socket can be active.
    const activationBarrier = previousDeactivation.current
    void activationBarrier.then(() => {
      if (isCurrentClient() && !terminalAuthenticationFailure) client.activate()
    })
    return () => {
      disposed = true
      if (activeClient.current === client) activeClient.current = null
      window.removeEventListener('focus', reconcileWhenActive)
      document.removeEventListener('visibilitychange', reconcileWhenActive)
      previousDeactivation.current = deactivateClient(client)
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

  const value = useMemo(() => ({ ...popups, connectionState, dismissNotification, clearPopups }), [
    clearPopups,
    connectionState,
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
