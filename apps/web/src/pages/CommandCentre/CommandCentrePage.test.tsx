import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import CommandCentrePage from './CommandCentrePage'
import { useMyEngagements, useStartEngagement } from '@/api/hooks/useEngagements'
import { useScenarioCatalog, useScenarioCatalogIndustries } from '@/api/hooks/useScenarios'
import { usePortfolioSummary } from '@/api/hooks/usePortfolio'
import type { Engagement, ScenarioCatalogPage } from '@/api/types'

// mock hooks and shared components for tests
vi.mock('@/api/hooks/useEngagements', () => ({
  useMyEngagements: vi.fn(),
  useStartEngagement: vi.fn(),
}))
vi.mock('@/api/hooks/useScenarios', () => ({
  useScenarioCatalog: vi.fn(),
  useScenarioCatalogIndustries: vi.fn(),
}))
vi.mock('@/api/hooks/usePortfolio', () => ({
  usePortfolioSummary: vi.fn(),
}))
vi.mock('@/components/shared/ObjectiveTourProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))
vi.mock('@/components/shared/LoadingState', () => ({
  default: () => <div>Loading...</div>,
}))

// typed mock references
const mockedMyEngagements = vi.mocked(useMyEngagements)
const mockedStartEngagement = vi.mocked(useStartEngagement)
const mockedScenarioCatalog = vi.mocked(useScenarioCatalog)
const mockedCatalogIndustries = vi.mocked(useScenarioCatalogIndustries)
const mockedPortfolio = vi.mocked(usePortfolioSummary)

// mock scrollIntoView for the tests
beforeEach(() => {
  vi.clearAllMocks()
  Element.prototype.scrollIntoView = vi.fn()
})

// creates an engagement object for tests with optional overrides
function makeEngagement(overrides: Partial<Engagement>): Engagement {
  return {
    id: 'eng-1',
    userId: 'user-1',
    scenarioId: 'scenario-1',
    personaId: 'persona-1',
    state: 'CLIENT_INTELLIGENCE',
    selectedLeadId: 'lead-1',
    createdAt: '2026-08-01T10:00:00Z',
    completedAt: null,
    events: [],
    scenarioTitle: 'Claims Modernisation',
    scenarioIndustry: 'Insurance',
    leadCompanyName: 'Acme Insurance',
    phase: 'CLIENT_INTELLIGENCE',
    phaseLabel: 'Research the client',
    progressPercent: 40,
    nextAction: 'Gather more evidence',
    evidenceCount: 2,
    daysElapsed: 1,
    meetingId: null,
    ...overrides,
  } as Engagement
}

// creates an empty scenario catalogue for tests where catalogue data is not needed
const emptyCatalogue: ScenarioCatalogPage = {
  items: [],
  page: 0,
  size: 8,
  totalElements: 0,
  totalPages: 1,
} as unknown as ScenarioCatalogPage

// sets up mocked api data used
function setup(engagements: Engagement[]) {
  mockedMyEngagements.mockReturnValue({
    data: engagements,
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useMyEngagements>)

  mockedStartEngagement.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useStartEngagement>)

  mockedScenarioCatalog.mockReturnValue({
    data: emptyCatalogue,
    isLoading: false,
    isFetching: false,
    isError: false,
  } as unknown as ReturnType<typeof useScenarioCatalog>)

  mockedCatalogIndustries.mockReturnValue({
    data: [],
  } as unknown as ReturnType<typeof useScenarioCatalogIndustries>)

  mockedPortfolio.mockReturnValue({
    data: {
      completedEngagements: 3,
      contractsWon: 2,
      contractsLost: 1,
      completedEngagementsHistory: [],
    },
    isLoading: false,
  } as unknown as ReturnType<typeof usePortfolioSummary>)
}

// renders the command centre inside a router for tests that use navigation
function renderPage() {
  return render(
    <MemoryRouter>
      <CommandCentrePage />
    </MemoryRouter>,
  )
}

describe('CommandCentrePage active engagement filter and sort dropdowns', () => {
  it('filters to only \'Ready for review\' engagements when that dropdown option is chosen', async () => {
    const user = userEvent.setup()
    setup([
      makeEngagement({
        id: 'eng-featured',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-08-05T10:00:00Z',
        nextAction: 'Featured next action',
      }),
      makeEngagement({
        id: 'eng-review',
        state: 'REVIEW',
        createdAt: '2026-08-01T10:00:00Z',
        scenarioTitle: 'Ready For Review Scenario',
        nextAction: 'Awaiting your review',
      }),
      makeEngagement({
        id: 'eng-other',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-07-01T10:00:00Z',
        scenarioTitle: 'Other Scenario',
        nextAction: 'Do more research',
      }),
    ])

    renderPage()

    // the most recently active engagement is featured and excluded from the compact list
    const filterDropdown = screen.getByText('All active')
    await user.click(filterDropdown)
    await user.click(await screen.findByRole('option', { name: 'Ready for review' }),)
    expect(screen.getByText('Ready For Review Scenario'),).toBeInTheDocument()
    expect(screen.queryByText('Other Scenario'),).not.toBeInTheDocument()
  })

  it('sorts list by scenario title when Scenario sort is chosen', async () => {
    const user = userEvent.setup()
    setup([
      makeEngagement({
        id: 'eng-featured',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-08-05T10:00:00Z',
      }),
      makeEngagement({
        id: 'eng-zzz',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-08-01T10:00:00Z',
        scenarioTitle: 'Test B Scenario',
      }),
      makeEngagement({
        id: 'eng-aaa',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-07-01T10:00:00Z',
        scenarioTitle: 'Test A Scenario',
      }),
    ])

    renderPage()

    // sort by recently active
    const sortDropdown = screen.getByText('Recently active')
    await user.click(sortDropdown)
    await user.click(await screen.findByText('Scenario'))

    // should display based on most recently active
    const titles = screen
      .getAllByText(/Scenario$/)
      .map((node) => node.textContent)
    const testIndexA = titles.indexOf('Test A Scenario')
    const testIndexB = titles.indexOf('Test B Scenario')
    expect(testIndexA).toBeGreaterThanOrEqual(0)
    expect(testIndexB).toBeGreaterThan(testIndexA)
  })

  it('resets back to All active when re-selected after filtering', async () => {
    const user = userEvent.setup()
    setup([
      makeEngagement({
        id: 'eng-featured',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-08-05T10:00:00Z',
      }),
      makeEngagement({
        id: 'eng-review',
        state: 'REVIEW',
        createdAt: '2026-08-01T10:00:00Z',
        scenarioTitle: 'Review Scenario',
      }),
      makeEngagement({
        id: 'eng-other',
        state: 'CLIENT_INTELLIGENCE',
        createdAt: '2026-07-01T10:00:00Z',
        scenarioTitle: 'Other Scenario',
      }),
    ])

    renderPage()

    const filterDropdown = screen.getByText('All active')
    await user.click(filterDropdown)
    await user.click(await screen.findByRole('option', { name: 'Ready for review' }),)
    expect(screen.queryByText('Other Scenario'),).not.toBeInTheDocument()

    // reselect All active to remove the current filter
    await user.click(screen.getByRole('combobox', { name: 'Filter' }),)
    await user.click(await screen.findByRole('option', { name: 'All active' }),)
    expect(screen.getByText('Other Scenario'),).toBeInTheDocument()
    expect(screen.getByText('Review Scenario'),).toBeInTheDocument()
  })
})

describe('CommandCentrePage scenario catalogue', () => {
  it('shows loading state while the catalogue page is being fetched', () => {
    setup([])
    mockedScenarioCatalog.mockReturnValue({
      data: emptyCatalogue,
      isLoading: false,
      isFetching: true,
      isError: false,
    } as unknown as ReturnType<typeof useScenarioCatalog>)
    renderPage()
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('filters the catalogue industry dropdown down to chosen industry', async () => {
    const user = userEvent.setup()
    setup([])
    mockedCatalogIndustries.mockReturnValue({
      data: ['Insurance', 'Retail'],
    } as unknown as ReturnType<typeof useScenarioCatalogIndustries>)

    renderPage()
    const industryDropdown = screen.getAllByText('All industries')[0]
    await user.click(industryDropdown)
    await user.click(await screen.findByText('Retail'))

    // selecting an industry should pass the underlying industry value to the API hook
    expect(mockedScenarioCatalog).toHaveBeenLastCalledWith(
      expect.objectContaining({
        industry: 'Retail',
      }),
    )
  })

  it('maps the difficulty dropdown label to its numeric value', async () => {
    const user = userEvent.setup()
    setup([])
    renderPage()

    // select advanced for difficulty dropdown
    const difficultyDropdown = screen.getByText('All difficulty')
    await user.click(difficultyDropdown)
    await user.click(await screen.findByText('Advanced'))

    // expects difficulty level 4
    expect(mockedScenarioCatalog).toHaveBeenLastCalledWith(
      expect.objectContaining({difficulty: 4,}),
    )
  })
})