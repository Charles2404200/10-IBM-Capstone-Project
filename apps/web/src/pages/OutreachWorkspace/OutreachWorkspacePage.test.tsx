import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import OutreachWorkspacePage from './OutreachWorkspacePage'
import {
  useCapabilityBrief,
  useOutreach,
  useSendOutreach,
  useSubmitCapabilityBrief,
} from '@/api/hooks/useOutreach'
import { useLeadIntelligence, useResearch } from '@/api/hooks/useLeads'
import type { LeadIntelligence, ResearchEvidence } from '@/api/types'

// mock outreach and research hooks used by the page
vi.mock('@/api/hooks/useOutreach', () => ({
  useOutreach: vi.fn(),
  useCapabilityBrief: vi.fn(),
  useSendOutreach: vi.fn(),
  useSubmitCapabilityBrief: vi.fn(),
}))

vi.mock('@/api/hooks/useLeads', () => ({
  useLeadIntelligence: vi.fn(),
  useResearch: vi.fn(),
}))

vi.mock('@/components/shared/ObjectiveTourProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock('@/components/shared/LoadingState', () => ({
  default: () => <div>Loading...</div>,
}))

const mockedOutreach = vi.mocked(useOutreach)
const mockedCapabilityBrief = vi.mocked(useCapabilityBrief)
const mockedSendOutreach = vi.mocked(useSendOutreach)
const mockedSubmitBrief = vi.mocked(useSubmitCapabilityBrief)
const mockedLeadIntelligence = vi.mocked(useLeadIntelligence)
const mockedResearch = vi.mocked(useResearch)

function makeEvidence(sequenceNo: number): ResearchEvidence {
  return {
    id: `evidence-${sequenceNo}`,
    engagementId: 'eng-1',
    note: `Client signal number ${sequenceNo}`,
    hypothesis: null,
    evidenceType: 'COMPANY_NEWS',
    sourceUrl: null,
    sourceTitle: `Signal ${sequenceNo}`,
    origin: 'AI_GENERATED',
    verificationStatus: 'VERIFIED',
    occurredOn: null,
    confidence: 'HIGH',
    relevanceScore: 80,
    sequenceNo,
    supportingEvidenceIds: [],
    createdAt: '2026-08-01T10:00:00Z',
  } as ResearchEvidence
}

const sendMutate = vi.fn()

function setup(evidence: ResearchEvidence[]) {
  mockedOutreach.mockReturnValue({
    data: [],
    isLoading: false,
  } as unknown as ReturnType<typeof useOutreach>)

  mockedCapabilityBrief.mockReturnValue({
    data: null,
  } as unknown as ReturnType<typeof useCapabilityBrief>)

  mockedSendOutreach.mockReturnValue({
    mutate: sendMutate,
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useSendOutreach>)

  mockedSubmitBrief.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useSubmitCapabilityBrief>)

  mockedLeadIntelligence.mockReturnValue({
    data: {
      companyName: 'Company Test',
      industry: 'Insurance',
      decisionMaker: {
        value: 'John Doe, CEO',
        revealedBy: [],
      },
    } as unknown as LeadIntelligence,
  } as unknown as ReturnType<typeof useLeadIntelligence>)

  mockedResearch.mockReturnValue({
    data: evidence,
  } as unknown as ReturnType<typeof useResearch>)
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/engagements/eng-1/outreach']}>
      <Routes>
        <Route
          path="/dashboard/engagements/:engagementId/outreach"
          element={<OutreachWorkspacePage />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('OutreachWorkspacePage evidence assistant', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sendMutate.mockClear()
  })

  it('appends the clicked evidence card note into the message body', async () => {
    const user = userEvent.setup()
    setup([makeEvidence(1)])
    renderPage()

    const evidenceCard = screen.getByRole('button', { name: /Client signal number 1/, })
    await user.click(evidenceCard)
    const messageField = screen.getByLabelText('Message') as HTMLTextAreaElement

    // clicking an evidence card should add its note to the message
    expect(messageField.value).toContain('Client signal number 1')
  })

  // changed after qa changes
  it('does not show a separate "evidence you can reference" strip outside the assist panel', () => {
    setup([makeEvidence(1)])
    renderPage()

    // evidence should now be contained within the assist panel
    expect(screen.queryByText('Grounded context')).not.toBeInTheDocument()
  })
})

describe('OutreachWorkspacePage outreach checklist', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sendMutate.mockClear()
  })

  it('keeps the checklist rail visible while composing', async () => {
    const user = userEvent.setup()
    setup([])
    renderPage()

    expect(screen.getByText('Outreach checklist')).toBeInTheDocument()
    expect(screen.getByText('0/4')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Message'), 'This is a test email.',)

    // the checklist should remain visible while the message is being composed
    expect(screen.getByText('Outreach checklist').closest('div'),).toBeTruthy()
  })
})