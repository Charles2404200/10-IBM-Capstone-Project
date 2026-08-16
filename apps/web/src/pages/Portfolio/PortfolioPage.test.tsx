import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { usePortfolioSummary, useReplayComparison } from '@/api/hooks/usePortfolio'
import { useMyAchievements } from '@/api/hooks/useAchievements'
import { useAuthStore } from '@/store/authStore'
import PortfolioPage from './PortfolioPage'
import type { CompetencyTrend } from '@/api/types'

// mock hocks and share compoents for tests
vi.mock('@/api/hooks/usePortfolio', () => ({
  usePortfolioSummary: vi.fn(),
  useReplayComparison: vi.fn(() => ({
    data: undefined,
    isFetching: false,
  })),
}))
vi.mock('@/api/hooks/useAchievements', () => ({ useMyAchievements: vi.fn()}))
vi.mock('@/store/authStore', () => ({ useAuthStore: vi.fn() }))
vi.mock('@/lifecycle/components/PageHeader', () => ({ default: () => <div>Page Header</div> }))
vi.mock('@/components/shared/LoadingState', () => ({ default: () => <div>Loading...</div> }))
vi.mock('@/components/shared/ErrorState', () => ({ default: () => <div>Error...</div>}))

// typed mock references
const mockedUsePortfolioSummary = vi.mocked(usePortfolioSummary)
const mockedUseReplayComparison = vi.mocked(useReplayComparison)
const mockedUseMyAchievements = vi.mocked(useMyAchievements)
const mockedUseAuthStore = vi.mocked(useAuthStore)

// creates a competency trend object for tests
function makeTrend( competencyName: string, points: CompetencyTrend['points']): CompetencyTrend {
  return { competencyName, points }
}

const basePortfolio = {
  totalEngagements: 3,
  contractsWon: 2,
  contractsLost: 1,
  averageOverallScore: 75,
  completedEngagementsHistory: [],
  competencyTrends: [],
}

// helper function to set up the mocked portfolio data for tests
function setupPortfolio(trends: CompetencyTrend[]) {
  mockedUsePortfolioSummary.mockReturnValue({
    data: {
      ...basePortfolio,
      competencyTrends: trends,
    },
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof usePortfolioSummary>)

  mockedUseReplayComparison.mockReturnValue({
    data: undefined,
    isFetching: false,
  } as unknown as ReturnType<typeof useReplayComparison>)

  mockedUseMyAchievements.mockReturnValue({
    data: [],
    isLoading: false,
  } as unknown as ReturnType<typeof useMyAchievements>)

  mockedUseAuthStore.mockReturnValue({
    displayName: 'Test User',
  } as unknown as ReturnType<typeof useAuthStore>)
}

// get the competency progression section from the rendered page
function getCompetencySection() {
  return screen
    .getByRole('heading', { name: 'Competency Progression' })
    .closest('section') as HTMLElement
}

// get all trend scores from the competency progression section
function getTrendScores() {
  const section = getCompetencySection()

  return within(section)
    .getAllByText(/^\d+$/)
    .filter((element) =>
      element.className.toString().includes('_trendScore_'),
    )
    .map((element) => Number(element.textContent))
}

describe('PortfolioPage competency progression', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('competency progression shows most recent attempt by default', () => {
    setupPortfolio([
      makeTrend('Communication', [
      {
        engagementId: 'engagement-1',
        generatedAt: '2026-08-01T10:00:00Z',
        score: 60,
      },
      {
        engagementId: 'engagement-2',
        generatedAt: '2026-08-10T10:00:00Z',
        score: 75,
      },
      {
        engagementId: 'engagement-3',
        generatedAt: '2026-08-15T10:00:00Z',
        score: 85,
      },
      ]),
    ])

    render(<PortfolioPage />)

    const section = getCompetencySection()

    expect(screen.getByText('Competency Progression')).toBeInTheDocument()
    expect(within(section).getByText('85')).toBeInTheDocument()
    expect(within(section).queryByText('60')).not.toBeInTheDocument()
    expect(within(section).queryByText('75')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View history' })).toBeInTheDocument()
  })

  it('competency progression shows all attempts when View history is clicked', () => {
    setupPortfolio([
      makeTrend('Communication', [
        {
          engagementId: 'engagement-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 60,
        },
        {
          engagementId: 'engagement-2',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 75,
        },
        {
          engagementId: 'engagement-3',
          generatedAt: '2026-08-15T10:00:00Z',
          score: 85,
        },
      ]),
    ])

    render(<PortfolioPage />)

    fireEvent.click(screen.getByRole('button', { name: 'View history' }))

    const section = getCompetencySection()

    expect(within(section).getByText('60')).toBeInTheDocument()
    expect(within(section).getByText('75')).toBeInTheDocument()
    expect(within(section).getByText('85')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hide history' })).toBeInTheDocument()
  })

  it('history should be hidden when Hide history is clicked', () => {
    setupPortfolio([
      makeTrend('Communication', [
        {
          engagementId: 'engagement-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 60,
        },
        {
          engagementId: 'engagement-2',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 75,
        },
        {
          engagementId: 'engagement-3',
          generatedAt: '2026-08-15T10:00:00Z',
          score: 85,
        },
      ]),
    ])

    render(<PortfolioPage />)

    fireEvent.click(screen.getByRole('button', { name: 'View history' }))
    fireEvent.click(screen.getByRole('button', { name: 'Hide history' }),)

    const section = getCompetencySection()

    expect(within(section).getByText('85')).toBeInTheDocument()
    expect(within(section).queryByText('60')).not.toBeInTheDocument()
    expect(within(section).queryByText('75')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View history' })).toBeInTheDocument()
  })

  it('history toggle applies to all competency cards', () => {
    setupPortfolio([
      makeTrend('Communication', [
        {
          engagementId: 'communication-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 60,
        },
        {
          engagementId: 'communication-2',
          generatedAt: '2026-08-15T10:00:00Z',
          score: 80,
        },
      ]),
      makeTrend('Negotiation', [
        {
          engagementId: 'negotiation-1',
          generatedAt: '2026-08-02T10:00:00Z',
          score: 65,
        },
        {
          engagementId: 'negotiation-2',
          generatedAt: '2026-08-15T11:00:00Z',
          score: 90,
        },
      ]),
      makeTrend('Commercial', [
        {
          engagementId: 'commercial-1',
          generatedAt: '2026-08-03T10:00:00Z',
          score: 55,
        },
        {
          engagementId: 'commercial-2',
          generatedAt: '2026-08-15T12:00:00Z',
          score: 70,
        },
      ]),
      makeTrend('Discovery', [
        {
          engagementId: 'discovery-1',
          generatedAt: '2026-08-04T10:00:00Z',
          score: 50,
        },
        {
          engagementId: 'discovery-2',
          generatedAt: '2026-08-15T13:00:00Z',
          score: 85,
        },
      ]),
    ])

    render(<PortfolioPage />)

    const section = getCompetencySection()

    // by default, the latest scores are shown for each competency
    expect(within(section).getByText('80')).toBeInTheDocument()
    expect(within(section).getByText('90')).toBeInTheDocument()
    expect(within(section).getByText('70')).toBeInTheDocument()
    expect(within(section).getByText('85')).toBeInTheDocument()

    expect(within(section).queryByText('60')).not.toBeInTheDocument()
    expect(within(section).queryByText('65')).not.toBeInTheDocument()
    expect(within(section).queryByText('55')).not.toBeInTheDocument()
    expect(within(section).queryByText('50')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'View history' }))

    // after clicking "View history", all scores for each competency should be shown
    expect(within(section).getByText('60')).toBeInTheDocument()
    expect(within(section).getByText('80')).toBeInTheDocument()
    expect(within(section).getByText('65')).toBeInTheDocument()
    expect(within(section).getByText('90')).toBeInTheDocument()
    expect(within(section).getByText('55')).toBeInTheDocument()
    expect(within(section).getByText('70')).toBeInTheDocument()
    expect(within(section).getByText('50')).toBeInTheDocument()
    expect(within(section).getByText('85')).toBeInTheDocument()
  })

  it('utilises generatedAt to calculate the latest attempt', () => {
    setupPortfolio([
      makeTrend('Communication', [
        {
          engagementId: 'engagement-latest',
          generatedAt: '2026-08-15T10:00:00Z',
          score: 90,
        },
        {
          engagementId: 'engagement-old',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 60,
        },
        {
          engagementId: 'engagement-middle',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 75,
        },
      ]),
    ])

    render(<PortfolioPage />)

    const section = getCompetencySection()

    // shows the latest attempt
    expect(within(section).getByText('90')).toBeInTheDocument()
    expect(within(section).queryByText('60')).not.toBeInTheDocument()
    expect(within(section).queryByText('75')).not.toBeInTheDocument()
  })

  it('displays competency history in chronological order', () => {
    setupPortfolio([
      makeTrend('Communication', [
        {
          engagementId: 'engagement-3',
          generatedAt: '2026-08-15T10:00:00Z',
          score: 90,
        },
        {
          engagementId: 'engagement-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 60,
        },
        {
          engagementId: 'engagement-2',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 75,
        },
      ]),
    ])

    render(<PortfolioPage />)

    fireEvent.click(screen.getByRole('button', { name: 'View history' }))
    const scores = getTrendScores()
    expect(scores).toEqual([60, 75, 90])
  })

  it('renders multiple competency series in the progression graph', () => {
    setupPortfolio([
      makeTrend('Communication', [
        {
          engagementId: 'engagement-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 60,
        },
        {
          engagementId: 'engagement-2',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 80,
        },
      ]),
      makeTrend('Negotiation', [
        {
          engagementId: 'engagement-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 70,
        },
        {
          engagementId: 'engagement-2',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 85,
        },
      ]),
    ])

    render(<PortfolioPage />)

    expect(screen.getByText('Progress Across Attempts')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Communication' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Negotiation' })).toBeInTheDocument()
  })

  it('does not render the progression graph when there is no competency data', () => {
    setupPortfolio([])
    render(<PortfolioPage />)
    expect(screen.queryByText('Progress Across Attempts')).not.toBeInTheDocument()
  })

  it('renders a history toggle only when a competency has multiple attempts', () => {
    setupPortfolio([
      makeTrend('Negotiation', [
        {
          engagementId: 'engagement-1',
          generatedAt: '2026-08-01T10:00:00Z',
          score: 80,
        },
        {
          engagementId: 'engagement-2',
          generatedAt: '2026-08-10T10:00:00Z',
          score: 85,
        },
      ]),
    ])

    render(<PortfolioPage />)
    expect(screen.getByRole('button', { name: 'View history' })).toBeInTheDocument()
  })
})