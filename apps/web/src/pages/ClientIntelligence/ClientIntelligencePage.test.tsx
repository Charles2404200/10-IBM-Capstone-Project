import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import ClientIntelligencePage from './ClientIntelligencePage'
import { useAnalyzeUserContext, useCompleteResearch, useGenerateResearchIntelligence, useResearch, useResearchGateStatus, useSaveResearch, } from '@/api/hooks/useLeads'
import type { ResearchEvidence } from '@/api/types'

// mock hooks and shared components for tests
vi.mock('@/api/hooks/useLeads', () => ({
  useResearch: vi.fn(),
  useSaveResearch: vi.fn(),
  useGenerateResearchIntelligence: vi.fn(),
  useAnalyzeUserContext: vi.fn(),
  useResearchGateStatus: vi.fn(),
  useCompleteResearch: vi.fn(),
}))
vi.mock('@/components/shared/ObjectiveTourProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))
vi.mock('@/components/shared/LoadingState', () => ({
  default: () => <div>Loading...</div>,
}))
vi.mock('@/components/shared/ErrorState', () => ({
  default: () => <div>Error...</div>,
}))

// mock scrollIntoView for dropdowns
Element.prototype.scrollIntoView = vi.fn()

// typed mock references
const mockedResearch = vi.mocked(useResearch)
const mockedSaveResearch = vi.mocked(useSaveResearch)
const mockedGenerateResearch = vi.mocked(useGenerateResearchIntelligence)
const mockedAnalyzeContext = vi.mocked(useAnalyzeUserContext)
const mockedGateStatus = vi.mocked(useResearchGateStatus)
const mockedCompleteResearch = vi.mocked(useCompleteResearch)

// default research gate data used by the page during tests
const gate = {
  researchCompleted: false,
  evidenceCount: 1,
  requiredEvidenceCount: 2,
  hasStakeholderEvidence: false,
  hasHypothesis: false,
  confidencePercent: 20,
  requiredConfidencePercent: 40,
  ready: false,
  coverageCount: 1,
  requiredCoverageCount: 2,
  groundedHypothesis: false,
  reliabilityScore: 20,
  verificationScore: 20,
  relevanceScore: 20,
  coaching: [],
}

const saveMutate = vi.fn()

// sets up the mocked api hooks with the data needed by the page
function setup(evidence: ResearchEvidence[]) {
  mockedResearch.mockReturnValue({
    data: evidence,
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useResearch>)

  mockedSaveResearch.mockReturnValue({
    mutate: saveMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useSaveResearch>)

  mockedGenerateResearch.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    reset: vi.fn(),
  } as unknown as ReturnType<typeof useGenerateResearchIntelligence>)

  mockedAnalyzeContext.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    reset: vi.fn(),
  } as unknown as ReturnType<typeof useAnalyzeUserContext>)

  mockedGateStatus.mockReturnValue({
    data: gate,
  } as unknown as ReturnType<typeof useResearchGateStatus>)

  mockedCompleteResearch.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useCompleteResearch>)
}

// renders the page at the same route used by the real application
function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/engagements/eng-1/intelligence']}>
      <Routes>
        <Route
          path="/dashboard/engagements/:engagementId/intelligence"
          element={<ClientIntelligencePage />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ClientIntelligencePage manual evidence dropdowns', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    saveMutate.mockClear()
  })

  it('submits the evidence type and confidence actually selected from the dropdowns, not the defaults', async () => {
    const user = userEvent.setup()
    setup([])
    renderPage()

    await user.click(screen.getByRole('button', { name: /Add source/i }))
    const modal = screen.getByRole('dialog', { name: 'Add a source to the evidence board', })

    // selects a different evidence type from the default value
    await user.click(within(modal).getByText('COMPANY NEWS'))
    await user.click(await screen.findByRole('option', { name: 'STAKEHOLDER PROFILE' }),)

    // selects a different confidence level from the default value
    await user.click(within(modal).getByText('MEDIUM'))
    await user.click(await screen.findByRole('option', { name: 'HIGH' }))

    // type finding mock data and submit
    await user.type(within(modal).getByLabelText('Finding'), 'Test finding.',)
    await user.click(within(modal).getByRole('button', { name: 'Add evidence' }),)

    // verifies the selected dropdown values are submitted instead of the defaults
    expect(saveMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        evidenceType: 'STAKEHOLDER_PROFILE',
        confidence: 'HIGH',
        note: 'Test finding.',
      }),
      expect.anything(),
    )
  })
})