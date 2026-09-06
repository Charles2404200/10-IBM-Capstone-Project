import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AdminConsolePage from './AdminConsolePage'
import { useAdminAiOperations } from '@/api/hooks/useAdminAiOperations'
import { useAdminPlatformOverview } from '@/api/hooks/useAdminPlatformOverview'
import { useAllScenariosForAdmin } from '@/api/hooks/useAdminScenarios'
import { useAuthStore } from '@/store/authStore'

// mock hooks and shared components for tests
vi.mock('@/api/hooks/useAdminAiOperations', () => ({ useAdminAiOperations: vi.fn() }))
vi.mock('@/api/hooks/useAdminPlatformOverview', () => ({ useAdminPlatformOverview: vi.fn() }))
vi.mock('@/api/hooks/useAdminScenarios', () => ({ useAllScenariosForAdmin: vi.fn() }))
vi.mock('@/store/authStore', () => ({ useAuthStore: vi.fn() }))
vi.mock('@/components/shared/LoadingState', () => ({ default: () => <div>Loading...</div> }))
vi.mock('@/components/shared/ErrorState', () => ({ default: () => <div>Error...</div> }))

// typed mock references
const mockedAiOps = vi.mocked(useAdminAiOperations)
const mockedPlatform = vi.mocked(useAdminPlatformOverview)
const mockedScenarios = vi.mocked(useAllScenariosForAdmin)
const mockedAuth = vi.mocked(useAuthStore)

const mocks = vi.hoisted(() => ({
  role: 'ADMINISTRATOR',
}))
// default data for test purposes
const basePlatform = {
  activeEngagements: 4,
  totalEngagements: 10,
  completionRatePercent: 50,
  averageAssessmentScore: 82,
  scenariosByStatus: { ACTIVE: 3, DRAFT: 1 },
  scenarios: [{ scenarioId: 's1', title: 'Onboarding', engagementCount: 5, completedCount: 3, averageAssessmentScore: 80 }],
}

vi.mock('@/store/authStore', () => ({
  useAuthStore: (selector: (state: { role: string }) => unknown) => selector({ role: mocks.role }),
}))
// sets up the mocked hooks for a given role
function setupAsRole(role: 'ADMINISTRATOR' | 'SCENARIO_AUTHOR' | 'REVIEWER') {
  mockedAuth.mockImplementation((selector) => selector({ role } as ReturnType<typeof useAuthStore.getState>),)
  mockedScenarios.mockReturnValue({ data: [], isLoading: false, isError: false, isFetching: false, refetch: vi.fn() } as unknown as ReturnType<typeof useAllScenariosForAdmin>)
  mockedAiOps.mockReturnValue({ data: { mockMode: false, parallelEnabled: true, parallelMaxCandidates: 3, providers: [], routing: {} }, isLoading: false, isError: false, isFetching: false, refetch: vi.fn() } as unknown as ReturnType<typeof useAdminAiOperations>)
  mockedPlatform.mockReturnValue({ data: basePlatform, isLoading: false, isError: false, isFetching: false, refetch: vi.fn() } as unknown as ReturnType<typeof useAdminPlatformOverview>)
}

vi.mock('@/api/hooks/useAdminScenarios', () => ({
  useAllScenariosForAdmin: () => ({
    data: [],
    isLoading: false,
    isFetching: false,
    refetch: vi.fn(),
  }),
}))
function renderPage() {
  return render(<MemoryRouter><AdminConsolePage /></MemoryRouter>)
}

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
describe('AdminConsolePage states', () => {
  beforeEach(() => vi.clearAllMocks())

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
  // tests admin data loading and dashboard rendering
  it('shows the loading state on first load', () => {
    setupAsRole('ADMINISTRATOR')
    mockedPlatform.mockReturnValue({ data: undefined, isLoading: true, isError: false, isFetching: true, refetch: vi.fn() } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

describe('AdminConsolePage notification navigation', () => {
  beforeEach(() => {
    mocks.role = 'ADMINISTRATOR'
  // api failure state when a role-relevant query fails
  it('shows the error state when a role-relevant query fails', () => {
    setupAsRole('ADMINISTRATOR')
    mockedPlatform.mockReturnValue({ data: undefined, isLoading: false, isError: true, isFetching: false, refetch: vi.fn() } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.getByText('Error...')).toBeInTheDocument()
  })

  it('shows administrators a link to publish notifications', () => {
    render(<MemoryRouter><AdminConsolePage /></MemoryRouter>)
  // api failure state when a role-irrelevant query fails
  it('does not error on a failed query the current role does not depend on', () => {
    // a scenario author never queries platform overview, so its failure should not block the page
    setupAsRole('SCENARIO_AUTHOR')
    mockedPlatform.mockReturnValue({ data: undefined, isLoading: false, isError: true, isFetching: false, refetch: vi.fn() } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.queryByText('Error...')).not.toBeInTheDocument()
  })

    expect(screen.getByRole('link', { name: /Send notifications/ }))
      .toHaveAttribute('href', '/dashboard/admin/notify')
  // empty state when no scenarios exist
  it('shows the empty-activity message when there are no scenarios yet', () => {
    setupAsRole('ADMINISTRATOR')
    mockedPlatform.mockReturnValue({ data: { ...basePlatform, scenarios: [] }, isLoading: false, isError: false, isFetching: false, refetch: vi.fn() } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.getByText('Learning activity will appear when learners begin a scenario.')).toBeInTheDocument()
  })

  // scenario rows when activity exists
  it('shows scenario rows when activity exists', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByText('Onboarding')).toBeInTheDocument()
  })
})

  it('does not show the publishing link to non-administrators', () => {
    mocks.role = 'REVIEWER'
describe('AdminConsolePage role-based visibility and navigation', () => {
  beforeEach(() => vi.clearAllMocks())

  // administrator-only visibility
  it('shows the People and Access card only for administrators', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByText('People and access')).toBeInTheDocument()
  })

    render(<MemoryRouter><AdminConsolePage /></MemoryRouter>)
  // administrator-only visibility for non-admin roles
  it('hides administrator-only cards from a reviewer', () => {
    setupAsRole('REVIEWER')
    renderPage()
    expect(screen.queryByText('People and access')).not.toBeInTheDocument()
    expect(screen.queryByText('Progression and badges')).not.toBeInTheDocument()
  })

    expect(screen.queryByRole('link', { name: /Send notifications/ })).not.toBeInTheDocument()
  // platform overview query is not called for non-admin roles
  it('does not fetch platform overview for a non-administrator role', () => {
    setupAsRole('REVIEWER')
    renderPage()
    expect(mockedPlatform).toHaveBeenCalledWith(false)
  })

  // navigation between people and access card and user management route
  it('links the People and Access card to the user management route', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByRole('link', { name: /People and access/i })).toHaveAttribute('href', '/dashboard/admin/users')
  })

  // navigation between ai delivery card and ai operations route
  it('links the AI delivery card to the AI operations route', () => {
      setupAsRole('ADMINISTRATOR')
      renderPage()

      expect(screen.getByRole('link', {name: /AI delivery.*View AI health/i,}),).toHaveAttribute('href', '/dashboard/admin/ai-operations')
  })
})