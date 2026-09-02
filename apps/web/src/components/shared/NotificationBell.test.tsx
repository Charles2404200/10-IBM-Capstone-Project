import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import NotificationBell from './NotificationBell'

const mocks = vi.hoisted(() => ({ useUnreadNotificationCount: vi.fn() }))
vi.mock('@/api/hooks/useNotifications', () => ({
  useUnreadNotificationCount: mocks.useUnreadNotificationCount,
}))

describe('NotificationBell', () => {
  it('caps the visible badge at 99+ and exposes the exact accessible count', () => {
    mocks.useUnreadNotificationCount.mockReturnValue({ data: { unreadCount: 145 }, isError: false })
    render(<MemoryRouter><NotificationBell /></MemoryRouter>)

    expect(screen.getByText('99+')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications, 145 unread' })).toBeInTheDocument()
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

