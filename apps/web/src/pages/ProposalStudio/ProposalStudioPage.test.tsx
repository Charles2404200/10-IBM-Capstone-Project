import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import ProposalStudioPage from './ProposalStudioPage'
import { useProposalStudio } from '@/features/proposal/hooks/useProposalStudio'
import { createEmptyProposalDraft } from '@/features/proposal/services/proposalDraftService'
import type { ProposalSource } from '@/api/types'

// mock hooks and shared components for tests
vi.mock('@/features/proposal/hooks/useProposalStudio', () => ({
  useProposalStudio: vi.fn(),
}))
vi.mock('@/components/shared/ObjectiveTourProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

// typed mock reference
const mockedUseProposalStudio = vi.mocked(useProposalStudio)

// creates a proposal source object for tests
function makeSource(id: string): ProposalSource {
  return {
    id,
    label: `Source ${id}`,
    type: 'RESEARCH_EVIDENCE',
    content: `Content for ${id}`,
    reliability: 'HIGH',
  }
}

// sets up the mocked proposal studio data for tests
function setup(sources: ProposalSource[]) {
  mockedUseProposalStudio.mockReturnValue({
    workspace: {
      data: {
        sources,
        proposal: undefined,
      },
      isLoading: false,
      isError: false,
    },
    proposal: undefined,
    submitted: false,
    draft: createEmptyProposalDraft(),
    activeSection: 'PROBLEM',
    review: null,
    saveState: 'idle',
    updateDraft: vi.fn(),
    setActiveSection: vi.fn(),
    attach: vi.fn(),
    detach: vi.fn(),
    attachedSourceIds: new Set<string>(),
    reviewCurrentDraft: vi.fn(),
    challengeCurrentDraft: vi.fn(),
    submit: vi.fn(),
    saveDraft: { isPending: false },
    reviewProposal: {
      isPending: false,
      isError: false,
      error: null,
    },
    challengeProposal: {
      isPending: false,
      data: undefined,
    },
    submitProposal: {
      isPending: false,
      isError: false,
      error: null,
    },
  } as unknown as ReturnType<typeof useProposalStudio>)
}

// renders the proposal studio at the expected engagement route
function renderPage() {
  return render(
    <MemoryRouter
      initialEntries={['/dashboard/engagements/eng-1/proposal']}
    >
      <Routes>
        <Route
          path="/dashboard/engagements/:engagementId/proposal"
          element={<ProposalStudioPage />}
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProposalStudioPage evidence library pagination', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows 2 sources per page and reports correct range for an odd total', () => {
    const sources = [
      makeSource('a'),
      makeSource('b'),
      makeSource('c'),
    ]
    setup(sources)
    renderPage()

    expect(screen.getByText(/Showing 1-2 of 3/)).toBeInTheDocument()
    expect(screen.getByText('Source a')).toBeInTheDocument()
    expect(screen.getByText('Source b')).toBeInTheDocument()
    expect(screen.queryByText('Source c')).not.toBeInTheDocument()
  })

  it('moves to next page of 2 and shows the remaining source', async () => {
    const user = userEvent.setup()
    const sources = [
      makeSource('a'),
      makeSource('b'),
      makeSource('c'),
    ]
    setup(sources)
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Next sources' }),)
    expect(screen.getByText(/Showing 3-3 of 3/)).toBeInTheDocument()
    expect(screen.getByText('Source c')).toBeInTheDocument()
    expect(screen.queryByText('Source a')).not.toBeInTheDocument()
  })

  it('shows exactly 1 pages with no pagination control when total is exactly 2', () => {
    const sources = [
      makeSource('a'),
      makeSource('b'),
    ]
    setup(sources)
    renderPage()

    expect(screen.getByText(/Showing 1-2 of 2/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Next sources' }),).not.toBeInTheDocument()
  })
})

describe('ProposalStudioPage header actions row', () => {
  beforeEach(() => vi.clearAllMocks())

  // qa changes slightly altered save status text, so this ensures the other buttons were not affected
  it('keeps Review proposal and Submit to client visible together regardless of save status text', () => {
    setup([])
    renderPage()

    expect(screen.getByRole('button', { name: 'Review proposal' }),).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit to client' }),).toBeInTheDocument()
  })
})