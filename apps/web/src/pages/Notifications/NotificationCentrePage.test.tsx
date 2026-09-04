import { fireEvent, render, screen } from '@testing-library/react'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationCentrePage from './NotificationCentrePage'

const fullMessage = `Full detail ${'x'.repeat(1000)}`
const markRead = vi.fn()
const fetchNextPage = vi.fn()
const matchMedia = vi.fn()
const mocks = vi.hoisted(() => ({
  useNotifications: vi.fn(),
  useNotificationDetail: vi.fn(),
  useMarkNotificationRead: vi.fn(),
}))

vi.mock('@/api/hooks/useNotifications', () => ({
  useNotifications: mocks.useNotifications,
  useNotificationDetail: mocks.useNotificationDetail,
  useMarkNotificationRead: mocks.useMarkNotificationRead,
}))

beforeAll(() => {
  vi.stubGlobal('matchMedia', matchMedia)
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    unobserve() {}
    disconnect() {}
  })
})

beforeEach(() => {
  mocks.useNotifications.mockReset()
  mocks.useNotificationDetail.mockReset()
  mocks.useMarkNotificationRead.mockReset()
  markRead.mockReset()
  fetchNextPage.mockReset()
  matchMedia.mockReturnValue({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })
})

function configureNotificationHooks(hasNextPage = false) {
  mocks.useNotifications.mockReturnValue({
    data: { pages: [{
      items: [{
        eventId: 'event-1',
        topicName: 'Course published',
        messagePreview: 'A short preview\u2026',
        priority: 'IMPORTANT',
        createdAt: new Date().toISOString(),
        isRead: false,
      }],
      hasMore: hasNextPage,
      nextCursor: hasNextPage ? 'cursor' : null,
    }] },
    isPending: false,
    isError: false,
    hasNextPage,
    isFetchingNextPage: false,
    fetchNextPage,
  })
  mocks.useMarkNotificationRead.mockReturnValue({ mutate: markRead })
  mocks.useNotificationDetail.mockImplementation((eventId: string | null) => eventId ? {
    data: {
      eventId,
      topicName: 'Course published',
      message: fullMessage,
      priority: 'IMPORTANT',
      createdAt: new Date().toISOString(),
      read: true,
      readAt: new Date().toISOString(),
    },
    isPending: false,
    isError: false,
  } : { isPending: false })
}

describe('NotificationCentrePage', () => {
  it('renders loading, empty and recoverable error states', () => {
    mocks.useMarkNotificationRead.mockReturnValue({ mutate: markRead })
    mocks.useNotificationDetail.mockReturnValue({ isPending: false })
    mocks.useNotifications.mockReturnValue({ isPending: true, isError: false })
    const { rerender } = render(<NotificationCentrePage />)
    expect(screen.getByText('Loading notifications')).toBeInTheDocument()

    mocks.useNotifications.mockReturnValue({
      data: { pages: [{ items: [], hasMore: false, nextCursor: null }] },
      isPending: false,
      isError: false,
    })
    rerender(<NotificationCentrePage />)
    expect(screen.getByText('You have no notifications.')).toBeInTheDocument()

    const refetch = vi.fn()
    mocks.useNotifications.mockReturnValue({ isPending: false, isError: true, refetch })
    rerender(<NotificationCentrePage />)
    expect(screen.getByText('Unable to load notifications')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(refetch).toHaveBeenCalled()
  })

  it('renders summary only, then loads full detail and marks unread item read', () => {
    configureNotificationHooks(true)

    render(<NotificationCentrePage />)

    expect(screen.getByText('A short preview\u2026')).toBeInTheDocument()
    expect(screen.queryByText(fullMessage)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Course published/ }))
    expect(screen.getByRole('button', { name: /Course published/ })).toHaveAttribute('aria-current', 'true')
    expect(screen.getByText(fullMessage)).toBeInTheDocument()
    expect(markRead).toHaveBeenCalledWith('event-1')
    expect(screen.getByLabelText('Notification list')).toBeInTheDocument()
    expect(screen.getByLabelText('Notification details')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Load more notifications' }))
    expect(fetchNextPage).toHaveBeenCalled()
  })

  it('shows an explicit priority label and a detail loading state', () => {
    configureNotificationHooks()
    mocks.useNotificationDetail.mockReturnValue({ isPending: true, isError: false })

    render(<NotificationCentrePage />)
    expect(screen.getByText('IMPORTANT')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Course published/ }))
    expect(screen.getByText('Loading notification details')).toBeInTheDocument()
  })

  it('opens full detail in an accessible modal on mobile and closes it', () => {
    matchMedia.mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    configureNotificationHooks()

    render(<NotificationCentrePage />)
    fireEvent.click(screen.getByRole('button', { name: /Course published/ }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(fullMessage)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    // Carbon retains the closed modal shell for focus management; its content must be removed.
    expect(screen.queryByText(fullMessage)).not.toBeInTheDocument()
  })
})
