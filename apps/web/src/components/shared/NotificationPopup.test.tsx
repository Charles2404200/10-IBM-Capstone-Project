import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import NotificationPopup from './NotificationPopup'

const mocks = vi.hoisted(() => ({ useNotification: vi.fn() }))

vi.mock('@/api/hooks/useNotification', () => ({ useNotification: mocks.useNotification }))

const notification = (priority: 'NORMAL' | 'IMPORTANT' | 'CRITICAL') => ({
  eventId: crypto.randomUUID(),
  topicName: `${priority} notice`,
  messagePreview: 'A bounded preview.',
  message: 'A bounded preview.',
  role: 'LEARNER' as const,
  priority,
  createdAt: new Date().toISOString(),
})
describe('NotificationPopup priority and overflow', () => {
  it('renders important politely and critical assertively', () => {
    mocks.useNotification.mockReturnValue({
      visible: [notification('IMPORTANT'), notification('CRITICAL')],
      overflowCount: 0,
      dismissNotification: vi.fn(),
      clearPopups: vi.fn(),
    })
    render(<MemoryRouter><NotificationPopup /></MemoryRouter>)

    expect(screen.getByText('Important: IMPORTANT notice')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('CRITICAL notice')
  })

  it('opens the durable Notification Centre from the aggregate', () => {
    const clearPopups = vi.fn()
    mocks.useNotification.mockReturnValue({
      visible: [notification('NORMAL')],
      overflowCount: 47,
      dismissNotification: vi.fn(),
      clearPopups,
    })
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/dashboard" element={<NotificationPopup />} />
          <Route path="/dashboard/notifications" element={<h1>Notification Centre</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: /47 additional notifications/i }))
    expect(screen.getByRole('heading', { name: 'Notification Centre' })).toBeInTheDocument()
    expect(clearPopups).toHaveBeenCalled()
  })
})
