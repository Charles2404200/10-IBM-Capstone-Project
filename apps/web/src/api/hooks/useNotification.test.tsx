import { StrictMode } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, useQuery, type InfiniteData } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationPage, UnreadNotificationCount } from '@/api/types'
import { notificationKeys } from './useNotifications'
import { NotificationRealtimeProvider, useNotification } from './useNotification'

interface TestStompFrame {
  headers: { message?: string }
  body: string
}

interface TestStompConfig {
  reconnectDelay?: number
  reconnectTimeMode?: number
  maxReconnectDelay?: number
}

interface TestStompClient {
  config: TestStompConfig
  onConnect: () => void
  onWebSocketClose: () => void
  onWebSocketError: () => void
  onStompError: (frame: TestStompFrame) => void
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>
  messageHandler?: (message: { body: string }) => void
}

const stomp = vi.hoisted(() => ({
  instances: [] as TestStompClient[],
  deactivationBarrier: undefined as Promise<void> | undefined,
}))
const auth = vi.hoisted(() => ({
  state: {
    token: 'signed-token' as string | null,
    role: 'LEARNER' as string | null,
    logout: vi.fn(),
  },
}))

vi.mock('@stomp/stompjs', () => ({
  ReconnectionTimeMode: { LINEAR: 0, EXPONENTIAL: 1 },
  Client: class implements TestStompClient {
    config: TestStompConfig
    onConnect = () => undefined
    onWebSocketClose = () => undefined
    onWebSocketError = () => undefined
    onStompError = () => undefined
    activate = vi.fn()
    deactivate = vi.fn(() => stomp.deactivationBarrier ?? Promise.resolve())
    messageHandler?: (message: { body: string }) => void
    subscribe = vi.fn((_destination: string, handler: (message: { body: string }) => void) => {
      this.messageHandler = handler
      return { unsubscribe: vi.fn() }
    })

    constructor(config: TestStompConfig) {
      this.config = config
      stomp.instances.push(this)
    }
  },
}))

vi.mock('@/store/authStore', () => {
  const useAuthStore = Object.assign(
    (selector: (state: typeof auth.state) => unknown) => selector(auth.state),
    { getState: () => auth.state },
  )
  return { useAuthStore }
})

function RealtimeProbe() {
  const notifications = useNotification()
  return (
    <div>
      <span data-testid="connection-state">{notifications.connectionState}</span>
      <span data-testid="popup-count">{notifications.visible.length}</span>
    </div>
  )
}

function RestPageProbe({ load }: {
  load: () => Promise<InfiniteData<NotificationPage, string | null>>
}) {
  const page = useQuery({ queryKey: notificationKeys.pages, queryFn: load })
  return <div>{page.data?.pages[0].items[0]?.topicName ?? 'No notifications'}</div>
}

function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderProvider(queryClient = createQueryClient(), strict = false) {
  const provider = (
    <QueryClientProvider client={queryClient}>
      <NotificationRealtimeProvider><RealtimeProbe /></NotificationRealtimeProvider>
    </QueryClientProvider>
  )
  return {
    queryClient,
    view: render(strict ? <StrictMode>{provider}</StrictMode> : provider),
  }
}

function notificationBody() {
  return JSON.stringify({
    eventId: '3ad8ff6f-f29a-4dea-8c09-833e6f46491d',
    topicName: 'Course published',
    messagePreview: 'A new course is ready',
    priority: 'IMPORTANT',
    role: 'LEARNER',
    createdAt: '2026-09-04T01:00:00.000Z',
  })
}

async function flushActivation() {
  await act(async () => {
    await Promise.resolve()
  })
}

describe('NotificationRealtimeProvider', () => {
  beforeEach(() => {
    stomp.instances.length = 0
    stomp.deactivationBarrier = undefined
    auth.state.token = 'signed-token'
    auth.state.role = 'LEARNER'
    auth.state.logout.mockReset()
    auth.state.logout.mockImplementation(() => {
      auth.state.token = null
      auth.state.role = null
    })
    window.history.replaceState({}, '', '/login')
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('configures STOMP-owned bounded exponential reconnection', () => {
    renderProvider()

    expect(stomp.instances[0].config).toMatchObject({
      reconnectDelay: 1_000,
      reconnectTimeMode: 1,
      maxReconnectDelay: 30_000,
    })
  })

  it('updates loaded caches once per event and reconciles after a burst', () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData<UnreadNotificationCount>(notificationKeys.unreadCount, { unreadCount: 4 })
    queryClient.setQueryData<InfiniteData<NotificationPage, string | null>>(notificationKeys.pages, {
      pageParams: [null],
      pages: [{ items: [], nextCursor: null, hasMore: false }],
    })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    renderProvider(queryClient)
    const socket = stomp.instances[0]
    act(() => socket.onConnect())

    act(() => socket.messageHandler?.({ body: notificationBody() }))
    act(() => socket.messageHandler?.({ body: notificationBody() }))

    expect(queryClient.getQueryData<UnreadNotificationCount>(notificationKeys.unreadCount))
      .toEqual({ unreadCount: 5 })
    expect(queryClient.getQueryData<InfiniteData<NotificationPage>>(notificationKeys.pages)
      ?.pages[0].items).toHaveLength(1)
    expect(screen.getByTestId('popup-count')).toHaveTextContent('1')

    act(() => vi.runOnlyPendingTimers())
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.pages })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.unreadCount })
  })

  it('reports failure, reconnects, resubscribes, and refreshes durable state', () => {
    const { queryClient } = renderProvider()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const socket = stomp.instances[0]

    act(() => socket.onConnect())
    expect(screen.getByTestId('connection-state')).toHaveTextContent('connected')
    expect(socket.subscribe).toHaveBeenCalledTimes(1)
    invalidate.mockClear()

    act(() => socket.onWebSocketClose())
    expect(screen.getByTestId('connection-state')).toHaveTextContent('reconnecting')
    expect(auth.state.logout).not.toHaveBeenCalled()

    act(() => socket.onConnect())
    expect(screen.getByTestId('connection-state')).toHaveTextContent('connected')
    expect(socket.subscribe).toHaveBeenCalledTimes(2)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.pages })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.unreadCount })
  })

  it('preserves existing popup state during a transient disconnect', () => {
    renderProvider()
    const socket = stomp.instances[0]
    act(() => socket.onConnect())
    act(() => socket.messageHandler?.({ body: notificationBody() }))
    expect(screen.getByTestId('popup-count')).toHaveTextContent('1')

    act(() => socket.onWebSocketClose())
    expect(screen.getByTestId('popup-count')).toHaveTextContent('1')
    act(() => socket.onConnect())
    expect(screen.getByTestId('popup-count')).toHaveTextContent('1')
  })

  it('reconciles durable state when the browser returns to the foreground', () => {
    const { queryClient } = renderProvider()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const socket = stomp.instances[0]
    act(() => socket.onConnect())
    invalidate.mockClear()

    act(() => window.dispatchEvent(new Event('focus')))

    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.pages })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.unreadCount })
  })

  it('shows a notification missed by WebSocket after foreground REST reconciliation', async () => {
    vi.useRealTimers()
    const queryClient = createQueryClient()
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

  it('deactivates on unmount and ignores callbacks from the disposed client', async () => {
    const { queryClient, view } = renderProvider()
    const socket = stomp.instances[0]
    await flushActivation()
    act(() => socket.onConnect())
    expect(socket.subscribe).toHaveBeenCalledTimes(1)

    view.unmount()
    expect(socket.deactivate).toHaveBeenCalledTimes(1)

    act(() => socket.onConnect())
    act(() => socket.messageHandler?.({ body: notificationBody() }))
    expect(socket.subscribe).toHaveBeenCalledTimes(1)
    expect(queryClient.getQueryData(notificationKeys.pages)).toBeUndefined()
  })

  it('serializes Strict Mode activation and leaves only the current client operative', async () => {
    let finishDeactivation: () => void = () => undefined
    stomp.deactivationBarrier = new Promise<void>((resolve) => {
      finishDeactivation = resolve
    })
    renderProvider(createQueryClient(), true)
    const [disposedClient, currentClient] = stomp.instances
    await flushActivation()

    expect(disposedClient.deactivate).toHaveBeenCalledTimes(1)
    expect(disposedClient.activate).not.toHaveBeenCalled()
    expect(currentClient.activate).not.toHaveBeenCalled()

    finishDeactivation()
    await flushActivation()
    expect(currentClient.activate).toHaveBeenCalledTimes(1)

    act(() => disposedClient.onConnect())
    act(() => currentClient.onConnect())
    expect(disposedClient.subscribe).not.toHaveBeenCalled()
    expect(currentClient.subscribe).toHaveBeenCalledTimes(1)
  })

  it('stops reconnecting and reuses session invalidation for an authentication failure', () => {
    renderProvider()
    const socket = stomp.instances[0]
    act(() => socket.onConnect())

    act(() => socket.onStompError({
      headers: { message: 'Invalid or missing authentication token' },
      body: '',
    }))

    expect(socket.deactivate).toHaveBeenCalledTimes(1)
    expect(auth.state.logout).toHaveBeenCalledTimes(1)
    act(() => socket.onConnect())
    expect(socket.subscribe).toHaveBeenCalledTimes(1)
  })

  it('does not let a stale socket authentication error clear a newer session', () => {
    renderProvider()
    const socket = stomp.instances[0]
    auth.state.token = 'replacement-token'

    act(() => socket.onStompError({
      headers: {},
      body: 'Unknown or inactive user',
    }))

    expect(socket.deactivate).toHaveBeenCalledTimes(1)
    expect(auth.state.logout).not.toHaveBeenCalled()
    expect(auth.state.token).toBe('replacement-token')
  })

  it('keeps the session and retry lifecycle for a non-authentication STOMP error', () => {
    renderProvider()
    const socket = stomp.instances[0]
    act(() => socket.onConnect())

    act(() => socket.onStompError({
      headers: { message: 'Broker temporarily unavailable' },
      body: 'Try again later',
    }))

    expect(screen.getByTestId('connection-state')).toHaveTextContent('reconnecting')
    expect(socket.deactivate).not.toHaveBeenCalled()
    expect(auth.state.logout).not.toHaveBeenCalled()
  })
})
