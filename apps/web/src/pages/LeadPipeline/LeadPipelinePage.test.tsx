import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import LeadPipelinePage from './LeadPipelinePage'
import { useEngagement } from '@/api/hooks/useEngagements'
import { useLeads, useSelectLead } from '@/api/hooks/useLeads'
import { useScenario } from '@/api/hooks/useScenarios'
import type { Engagement, LeadSummary, ScenarioSummary } from '@/api/types'

// mock hooks and shared components for tests
vi.mock('@/api/hooks/useEngagements', () => ({
  useEngagement: vi.fn(),
}))
vi.mock('@/api/hooks/useLeads', () => ({
  useLeads: vi.fn(),
  useSelectLead: vi.fn(),
}))
vi.mock('@/api/hooks/useScenarios', () => ({
  useScenario: vi.fn(),
}))
vi.mock('@/lifecycle/components/PageHeader', () => ({
  default: () => <div>Page Header</div>,
}))
vi.mock('@/components/shared/LoadingState', () => ({
  default: () => <div>Loading...</div>,
}))
vi.mock('@/components/shared/ErrorState', () => ({
  default: () => <div>Error...</div>,
}))

// typed mock references
const mockedEngagement = vi.mocked(useEngagement)
const mockedLeads = vi.mocked(useLeads)
const mockedSelectLead = vi.mocked(useSelectLead)
const mockedScenario = vi.mocked(useScenario)

// creates a lead object for tests
function makeLead(overrides: Partial<LeadSummary>): LeadSummary {
  return {
    id: 'lead-1',
    companyName: 'Test Company',
    industry: 'Retail',
    publicDescription: 'Test description.',
    difficulty: 'MEDIUM',
    signals: [],
    ...overrides,
  }
}

// sets up the mocked lead pipeline data for tests
function setup(leads: LeadSummary[]) {
  mockedEngagement.mockReturnValue({
    data: {
      id: 'eng-1',
      scenarioId: 'scenario-1',
      state: 'QUALIFYING',
      selectedLeadId: null,
    } as unknown as Engagement,
    isLoading: false,
  } as unknown as ReturnType<typeof useEngagement>)

  mockedLeads.mockReturnValue({
    data: leads,
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useLeads>)

  mockedScenario.mockReturnValue({
    data: undefined,
  } as unknown as ReturnType<typeof useScenario>)

  mockedSelectLead.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useSelectLead>)
}

// renders the lead pipeline at the expected engagement route
function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/engagements/eng-1/leads']}>
      <Routes>
        <Route
          path="/dashboard/engagements/:engagementId/leads"
          element={<LeadPipelinePage />}
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('LeadPipelinePage signal label capitalisation', () => {
  beforeEach(() => vi.clearAllMocks())

  it('capitalises a lowercase signal label', () => {
    setup([
      makeLead({
        signals: [
          {
            id: 's1',
            label: 'label capitalise test',
            category: 'FINANCIAL',
          },
        ],
      }),
    ])

    renderPage()

    expect(screen.getByText('Label capitalise test')).toBeInTheDocument()
    expect(screen.queryByText('label capitalise test'),).not.toBeInTheDocument()
  })

  it('does not double-capitalise or break an already-capitalised label', () => {
    setup([
      makeLead({
        signals: [
          {
            id: 's1',
            label: 'Label test',
            category: 'ORG',
          },
        ],
      }),
    ])

    renderPage()

    expect(screen.getByText('Label test')).toBeInTheDocument()
  })

  it('renders leads with no signals without throwing', () => {
    setup([makeLead({ signals: [] })])

    // ensures a lead without signal data still renders normally
    expect(() => renderPage()).not.toThrow()
    expect(screen.getByText('Test Company')).toBeInTheDocument()
  })
})