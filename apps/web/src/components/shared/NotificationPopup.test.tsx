import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import NotificationPopup from './NotificationPopup'

const mocks = vi.hoisted(() => ({
  useNotification: vi.fn(),
}))

vi.mock('@/api/hooks/useNotification', () => ({
  useNotification: mocks.useNotification,
}))

describe('NotificationPopup priority', () => {
  it('renders important domain priority as a visible non-emergency warning', () => {
    mocks.useNotification.mockReturnValue({
      notification: {
        eventId: 'event-important',
        userId: 'admin-1',
        topicName: 'New course published',
        message: 'A new course is available.',
        role: 'LEARNER',
        priority: 'IMPORTANT',
      },
      dismissNotification: vi.fn(),
    })

    render(<NotificationPopup />)

    expect(screen.getByText('Important: New course published')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('A new course is available.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('renders critical domain priority as an assertive alert', () => {
    mocks.useNotification.mockReturnValue({
      notification: {
        eventId: 'event-1',
        userId: 'admin-1',
        topicName: 'Security notice',
        message: 'Reset your password.',
        role: 'LEARNER',
        priority: 'CRITICAL',
      },
      dismissNotification: vi.fn(),
    })

    render(<NotificationPopup />)

    expect(screen.getByText('Critical: Security notice')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Reset your password.')
  })
})
