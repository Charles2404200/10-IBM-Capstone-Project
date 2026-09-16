import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import NotifyUsersPage from './NotifyUsersPage'

const mocks = vi.hoisted(() => ({ mutate: vi.fn() }))

vi.mock('@/api/hooks/useAdminPlatformOverview', () => ({
  useAdminNotifyUsers: () => ({
    mutate: mocks.mutate,
    isPending: false,
  }),
}))

describe('NotifyUsersPage priority', () => {
  beforeAll(() => {
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      unobserve() {}
      disconnect() {}
    })
  })

  beforeEach(() => {
    mocks.mutate.mockReset()
  })

  it('defaults admin notifications to normal priority', () => {
    render(<NotifyUsersPage />)

    expect(screen.getByLabelText('Priority')).toHaveValue('NORMAL')
    expect(screen.queryByText(/Critical notifications are expedited/)).not.toBeInTheDocument()
  })

  it('submits critical priority and displays its responsible-use warning', async () => {
    render(<NotifyUsersPage />)

    fireEvent.change(screen.getByLabelText('Topic name'), { target: { value: 'Urgent security notice' } })
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'Please reset your password.' } })
    fireEvent.click(screen.getByLabelText('Learners'))
    fireEvent.change(screen.getByLabelText('Priority'), { target: { value: 'CRITICAL' } })

    expect(screen.getByText(/Critical notifications are expedited/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Send notification' }))

    await waitFor(() => {
      expect(mocks.mutate).toHaveBeenCalledWith(
        {
          topicName: 'Urgent security notice',
          message: 'Please reset your password.',
          roles: ['LEARNER'],
          priority: 'CRITICAL',
        },
        expect.any(Object),
      )
    })
  })

  it('submits important priority for expedited non-emergency announcements', async () => {
    render(<NotifyUsersPage />)

    fireEvent.change(screen.getByLabelText('Topic name'), { target: { value: 'New course published' } })
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'A new course is available.' } })
    fireEvent.click(screen.getByLabelText('Learners'))
    fireEvent.change(screen.getByLabelText('Priority'), { target: { value: 'IMPORTANT' } })

    expect(screen.getByText(/Important notifications are expedited/)).toBeInTheDocument()
    expect(screen.queryByText(/Critical notifications are expedited/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Send notification' }))

    await waitFor(() => {
      expect(mocks.mutate).toHaveBeenCalledWith(
        {
          topicName: 'New course published',
          message: 'A new course is available.',
          roles: ['LEARNER'],
          priority: 'IMPORTANT',
        },
        expect.any(Object),
      )
    })
  })
})
