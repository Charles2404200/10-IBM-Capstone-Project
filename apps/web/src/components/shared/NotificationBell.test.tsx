import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import NotificationBell from './NotificationBell'

const mocks = vi.hoisted(() => ({ useUnreadNotificationCount: vi.fn() }))
vi.mock('@/api/hooks/useNotifications', () => ({
  useUnreadNotificationCount: mocks.useUnreadNotificationCount,
}))

describe('NotificationBell', () => {
  it.each([
    [0, null],
    [1, '1'],
    [17, '17'],
    [99, '99'],
  ])('renders an accessible badge for %i unread notifications', (count, visibleBadge) => {
    mocks.useUnreadNotificationCount.mockReturnValue({ data: { unreadCount: count }, isError: false })
    render(<MemoryRouter><NotificationBell /></MemoryRouter>)

    expect(screen.getByRole('button', { name: `Notifications, ${count} unread` })).toBeInTheDocument()
    if (visibleBadge === null) {
      expect(screen.queryByText('0')).not.toBeInTheDocument()
    } else {
      const badge = screen.getByText(visibleBadge)
      expect(badge).toHaveAttribute('aria-hidden', 'true')
      expect(badge.className).toContain('badge')
    }
  })

  it('caps the visible badge at 99+ and exposes the exact accessible count', () => {
    mocks.useUnreadNotificationCount.mockReturnValue({ data: { unreadCount: 100 }, isError: false })
    render(<MemoryRouter><NotificationBell /></MemoryRouter>)

    expect(screen.getByText('99+')).toBeInTheDocument()
    const button = screen.getByRole('button', { name: 'Notifications, 100 unread' })
    act(() => button.focus())
    expect(button).toHaveFocus()
  })

  it('uses client-side navigation to open the centre', () => {
    mocks.useUnreadNotificationCount.mockReturnValue({ data: { unreadCount: 3 }, isError: false })
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/dashboard" element={<NotificationBell />} />
          <Route path="/dashboard/notifications" element={<h1>Notification Centre</h1>} />
        </Routes>
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Notifications, 3 unread' }))
    expect(screen.getByRole('heading', { name: 'Notification Centre' })).toBeInTheDocument()
  })
})

