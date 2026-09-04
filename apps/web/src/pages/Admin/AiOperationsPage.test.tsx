import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AiOperationsPage from './AiOperationsPage'
import { useAdminAiOperations } from '@/api/hooks/useAdminAiOperations'

// mock hooks and shared components for tests
vi.mock('@/api/hooks/useAdminAiOperations', () => ({ useAdminAiOperations: vi.fn() }))
vi.mock('@/components/shared/LoadingState', () => ({ default: () => <div>Loading...</div> }))
vi.mock('@/components/shared/ErrorState', () => ({ default: () => <div>Error...</div> }))

const mockedAiOps = vi.mocked(useAdminAiOperations)

// sets up the mocked AI operations query for tests
function setup(overrides: Partial<ReturnType<typeof useAdminAiOperations>> = {}) {
  mockedAiOps.mockReturnValue({
    data: { mockMode: false, parallelEnabled: true, parallelMaxCandidates: 3, providers: [], routing: {} },
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useAdminAiOperations>)
}

describe('AiOperationsPage states', () => {
  beforeEach(() => vi.clearAllMocks())

  // loading state
  it('shows the loading state', () => {
    setup({ data: undefined, isLoading: true })
    render(<AiOperationsPage />)
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  // tests api failure state
  it('shows the error state on a failed query', () => {
    setup({ data: undefined, isError: true })
    render(<AiOperationsPage />)
    expect(screen.getByText('Error...')).toBeInTheDocument()
  })

  // tests api failure state when no data
  it('shows the error state when the query resolves with no data', () => {
    setup({ data: undefined, isError: false })
    render(<AiOperationsPage />)
    expect(screen.getByText('Error...')).toBeInTheDocument()
  })

  // empty state when no providers exist
  it('shows the empty-provider state when no providers are configured', () => {
    setup()
    render(<AiOperationsPage />)
    expect(screen.getByText('No AI providers configured')).toBeInTheDocument()
  })

  // test ai provider status display
  it('renders provider cards when providers exist', () => {
    setup({
      data: {
        mockMode: false, 
        parallelEnabled: true, 
        parallelMaxCandidates: 3, 
        routing: {},
        providers: [
          { providerId: 'gpt-5',
            available: true, 
            circuitState: 'CLOSED', 
            avgLatencyMs: 300, 
            requestsToday: 12, 
            successCount: 12,
            failureCount: 0, 
            fallbackRatePercent: 0, 
            quotaUsed: 10, 
            quotaLimit: 100 
          }
        ],
      },
    })

    render(<AiOperationsPage />)
    expect(screen.getByText('gpt-5')).toBeInTheDocument()
    expect(screen.getByText('Available')).toBeInTheDocument()
  })

  // test ai provider status display with fallback mode enabled
  it('warns when mock/fallback mode is enabled', () => {
    setup({ data: { mockMode: true, parallelEnabled: false, parallelMaxCandidates: 1, providers: [], routing: {} } })
    render(<AiOperationsPage />)
    expect(screen.getByText('Simulation fallback mode is enabled')).toBeInTheDocument()
  })
})