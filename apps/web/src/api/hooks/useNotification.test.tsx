import { act, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, useQuery, type InfiniteData } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationPage, UnreadNotificationCount } from '@/api/types'
import { notificationKeys } from './useNotifications'
import { NotificationRealtimeProvider, useNotification } from './useNotification'

interface TestStompClient {
  onConnect: () => void
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>
  messageHandler?: (message: { body: string }) => void
}

const stomp = vi.hoisted(() => ({ instances: [] as TestStompClient[] }))

vi.mock('@stomp/stompjs', () => ({
  Client: class implements TestStompClient {
    onConnect = () => undefined
    activate = vi.fn()
    deactivate = vi.fn(() => Promise.resolve())
    messageHandler?: (message: { body: string }) => void
    subscribe = vi.fn((_destination: string, handler: (message: { body: string }) => void) => {
      this.messageHandler = handler
    })

    constructor() {
      stomp.instances.push(this)
    }
  },
}))

vi.mock('@/store/authStore', () => ({
  useAuthStore: (selector: (state: { token: string; role: string }) => unknown) =>
    selector({ token: 'signed-token', role: 'LEARNER' }),
}))

function PopupProbe() {
  const notifications = useNotification()
  return <div data-testid="popup-count">{notifications.visible.length}</div>
}

function RestPageProbe({ load }: {
  load: () => Promise<InfiniteData<NotificationPage, string | null>>
}) {
  const page = useQuery({ queryKey: notificationKeys.pages, queryFn: load })
  return <div>{page.data?.pages[0].items[0]?.topicName ?? 'No notifications'}</div>
}

function createClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

describe('NotificationRealtimeProvider', () => {
  beforeEach(() => {
    stomp.instances.length = 0
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('updates loaded caches once per event and reconciles after a burst', () => {
    const queryClient = createClient()
    queryClient.setQueryData<UnreadNotificationCount>(notificationKeys.unreadCount, { unreadCount: 4 })
    queryClient.setQueryData<InfiniteData<NotificationPage, string | null>>(notificationKeys.pages, {
      pageParams: [null],
      pages: [{ items: [], nextCursor: null, hasMore: false }],
    })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    render(
      <QueryClientProvider client={queryClient}>
        <NotificationRealtimeProvider><PopupProbe /></NotificationRealtimeProvider>
      </QueryClientProvider>,
    )
    const socket = stomp.instances[0]
    act(() => socket.onConnect())
    const body = JSON.stringify({
      eventId: '3ad8ff6f-f29a-4dea-8c09-833e6f46491d',
      topicName: 'Course published',
      messagePreview: 'A new course is ready',
      priority: 'IMPORTANT',
      role: 'LEARNER',
      createdAt: '2026-09-04T01:00:00.000Z',
    })

    act(() => socket.messageHandler?.({ body }))
    act(() => socket.messageHandler?.({ body }))

    expect(queryClient.getQueryData<UnreadNotificationCount>(notificationKeys.unreadCount))
      .toEqual({ unreadCount: 5 })
    expect(queryClient.getQueryData<InfiniteData<NotificationPage>>(notificationKeys.pages)
      ?.pages[0].items).toHaveLength(1)
    expect(screen.getByTestId('popup-count')).toHaveTextContent('1')

    act(() => vi.runOnlyPendingTimers())
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.pages })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.unreadCount })
  })

  it('reconciles missed notifications after reconnect and browser focus', () => {
    const queryClient = createClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    render(
      <QueryClientProvider client={queryClient}>
        <NotificationRealtimeProvider><PopupProbe /></NotificationRealtimeProvider>
      </QueryClientProvider>,
    )
    const socket = stomp.instances[0]
    act(() => socket.onConnect())
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.pages })
    invalidate.mockClear()

    act(() => window.dispatchEvent(new Event('focus')))

    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.pages })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.unreadCount })
  })

  it('shows a notification missed by WebSocket after foreground REST reconciliation', async () => {
    vi.useRealTimers()
    const queryClient = createClient()
    let notificationAvailable = false
    const load = vi.fn(async (): Promise<InfiniteData<NotificationPage, string | null>> => ({
      pageParams: [null],
      pages: [{
        items: notificationAvailable ? [{
          eventId: '3ad8ff6f-f29a-4dea-8c09-833e6f46491d',
          topicName: 'Missed course announcement',
          messagePreview: 'Now available',
          priority: 'IMPORTANT',
          createdAt: '2026-09-04T01:00:00.000Z',
          isRead: false,
        }] : [],
        nextCursor: null,
        hasMore: false,
      }],
    }))
    render(
      <QueryClientProvider client={queryClient}>
        <NotificationRealtimeProvider>
          <RestPageProbe load={load} />
        </NotificationRealtimeProvider>
      </QueryClientProvider>,
    )
    await screen.findByText('No notifications')

    notificationAvailable = true
    act(() => window.dispatchEvent(new Event('focus')))

    await screen.findByText('Missed course announcement')
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2))
  })
})
