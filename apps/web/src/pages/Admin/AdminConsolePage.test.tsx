import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminConsolePage from './AdminConsolePage'

const mocks = vi.hoisted(() => ({
  role: 'ADMINISTRATOR',
}))

vi.mock('@/store/authStore', () => ({
  useAuthStore: (selector: (state: { role: string }) => unknown) => selector({ role: mocks.role }),
}))

vi.mock('@/api/hooks/useAdminScenarios', () => ({
  useAllScenariosForAdmin: () => ({
    data: [],
    isLoading: false,
    isFetching: false,
    refetch: vi.fn(),
  }),
}))

vi.mock('@/api/hooks/useAdminAiOperations', () => ({
  useAdminAiOperations: () => ({
    data: {
      providers: [],
      mockMode: false,
      parallelEnabled: false,
      parallelMaxCandidates: 1,
    },
    isLoading: false,
    isFetching: false,
    refetch: vi.fn(),
  }),
}))

vi.mock('@/api/hooks/useAdminPlatformOverview', () => ({
  useAdminPlatformOverview: () => ({
    data: {
      scenariosByStatus: {},
      activeEngagements: 0,
      totalEngagements: 0,
      completionRatePercent: 0,
      averageAssessmentScore: null,
      scenarios: [],
    },
    isLoading: false,
    isFetching: false,
    refetch: vi.fn(),
  }),
}))

describe('AdminConsolePage notification navigation', () => {
  beforeEach(() => {
    mocks.role = 'ADMINISTRATOR'
  })

  it('shows administrators a link to publish notifications', () => {
    render(<MemoryRouter><AdminConsolePage /></MemoryRouter>)

    expect(screen.getByRole('link', { name: /Send notifications/ }))
      .toHaveAttribute('href', '/dashboard/admin/notify')
  })

  it('does not show the publishing link to non-administrators', () => {
    mocks.role = 'REVIEWER'

    render(<MemoryRouter><AdminConsolePage /></MemoryRouter>)

    expect(screen.queryByRole('link', { name: /Send notifications/ })).not.toBeInTheDocument()
  })
})
