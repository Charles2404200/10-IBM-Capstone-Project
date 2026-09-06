import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAdminAiOperations } from '@/api/hooks/useAdminAiOperations'
import { useAdminPlatformOverview } from '@/api/hooks/useAdminPlatformOverview'
import { useAllScenariosForAdmin } from '@/api/hooks/useAdminScenarios'
import { useAuthStore } from '@/store/authStore'
import AdminConsolePage from './AdminConsolePage'

vi.mock('@/api/hooks/useAdminAiOperations', () => ({ useAdminAiOperations: vi.fn() }))
vi.mock('@/api/hooks/useAdminPlatformOverview', () => ({ useAdminPlatformOverview: vi.fn() }))
vi.mock('@/api/hooks/useAdminScenarios', () => ({ useAllScenariosForAdmin: vi.fn() }))
vi.mock('@/store/authStore', () => ({ useAuthStore: vi.fn() }))
vi.mock('@/components/shared/LoadingState', () => ({ default: () => <div>Loading...</div> }))
vi.mock('@/components/shared/ErrorState', () => ({ default: () => <div>Error...</div> }))

const mockedAiOps = vi.mocked(useAdminAiOperations)
const mockedPlatform = vi.mocked(useAdminPlatformOverview)
const mockedScenarios = vi.mocked(useAllScenariosForAdmin)
const mockedAuth = vi.mocked(useAuthStore)

const basePlatform = {
  activeEngagements: 4,
  totalEngagements: 10,
  completionRatePercent: 50,
  averageAssessmentScore: 82,
  scenariosByStatus: { ACTIVE: 3, DRAFT: 1 },
  scenarios: [{
    scenarioId: 's1',
    title: 'Onboarding',
    engagementCount: 5,
    completedCount: 3,
    averageAssessmentScore: 80,
  }],
}

function setupAsRole(role: 'ADMINISTRATOR' | 'SCENARIO_AUTHOR' | 'REVIEWER') {
  mockedAuth.mockImplementation((selector) =>
    selector({ role } as ReturnType<typeof useAuthStore.getState>))
  mockedScenarios.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAllScenariosForAdmin>)
  mockedAiOps.mockReturnValue({
    data: {
      mockMode: false,
      parallelEnabled: true,
      parallelMaxCandidates: 3,
      providers: [],
      routing: {},
    },
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAdminAiOperations>)
  mockedPlatform.mockReturnValue({
    data: basePlatform,
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAdminPlatformOverview>)
}

function renderPage() {
  return render(<MemoryRouter><AdminConsolePage /></MemoryRouter>)
}

describe('AdminConsolePage states', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the loading state on first load', () => {
    setupAsRole('ADMINISTRATOR')
    mockedPlatform.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      isFetching: true,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('shows the error state when a role-relevant query fails', () => {
    setupAsRole('ADMINISTRATOR')
    mockedPlatform.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isFetching: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.getByText('Error...')).toBeInTheDocument()
  })

  it('does not error on a failed query the current role does not depend on', () => {
    setupAsRole('SCENARIO_AUTHOR')
    mockedPlatform.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isFetching: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.queryByText('Error...')).not.toBeInTheDocument()
  })

  it('shows the empty-activity message when there are no scenarios yet', () => {
    setupAsRole('ADMINISTRATOR')
    mockedPlatform.mockReturnValue({
      data: { ...basePlatform, scenarios: [] },
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAdminPlatformOverview>)
    renderPage()
    expect(screen.getByText('Learning activity will appear when learners begin a scenario.'))
      .toBeInTheDocument()
  })

  it('shows scenario rows when activity exists', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByText('Onboarding')).toBeInTheDocument()
  })
})

describe('AdminConsolePage role-based visibility and navigation', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows administrator-only cards to administrators', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByText('People and access')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Send notifications/i }))
      .toHaveAttribute('href', '/dashboard/admin/notify')
  })

  it('hides administrator-only cards from a reviewer', () => {
    setupAsRole('REVIEWER')
    renderPage()
    expect(screen.queryByText('People and access')).not.toBeInTheDocument()
    expect(screen.queryByText('Progression and badges')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Send notifications/i })).not.toBeInTheDocument()
  })

  it('does not fetch platform overview for a non-administrator role', () => {
    setupAsRole('REVIEWER')
    renderPage()
    expect(mockedPlatform).toHaveBeenCalledWith(false)
  })

  it('links the People and Access card to the user management route', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByRole('link', { name: /People and access/i }))
      .toHaveAttribute('href', '/dashboard/admin/users')
  })

  it('links the AI delivery card to the AI operations route', () => {
    setupAsRole('ADMINISTRATOR')
    renderPage()
    expect(screen.getByRole('link', { name: /AI delivery.*View AI health/i }))
      .toHaveAttribute('href', '/dashboard/admin/ai-operations')
  })
})
