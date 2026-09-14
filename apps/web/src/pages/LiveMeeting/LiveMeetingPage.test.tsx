import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import LiveMeetingPage from './LiveMeetingPage'
import { useMeeting, useMeetingResponseOptions, useMeetingTranscript, usePersonaState, useRetryMeeting, } from '@/api/hooks/useMeeting'
import { useRetryEngagement } from '@/api/hooks/useEngagements'
import { useMeetingSocket } from '@/api/hooks/useMeetingSocket'
import type { ConversationTurn, Meeting, PersonaState } from '@/api/types'

Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
  configurable: true,
  value: vi.fn(),
})

// mock hocks and share components for tests
vi.mock('@/api/hooks/useMeeting', () => ({
  useMeeting: vi.fn(),
  useMeetingTranscript: vi.fn(),
  usePersonaState: vi.fn(),
  useMeetingResponseOptions: vi.fn(),
  useRetryMeeting: vi.fn(),
}))
vi.mock('@/api/hooks/useEngagements', () => ({ useRetryEngagement: vi.fn() }))
vi.mock('@/api/hooks/useMeetingSocket', () => ({ useMeetingSocket: vi.fn() }))
vi.mock('@/components/shared/ObjectiveTourProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))
vi.mock('@/components/shared/LoadingState', () => ({ default: () => <div>Loading...</div> }))
vi.mock('@/components/shared/ErrorState', () => ({ default: () => <div>Error...</div> }))

// typed mock references
const mockedMeeting = vi.mocked(useMeeting)
const mockedTranscript = vi.mocked(useMeetingTranscript)
const mockedPersonaState = vi.mocked(usePersonaState)
const mockedResponseOptions = vi.mocked(useMeetingResponseOptions)
const mockedRetryMeeting = vi.mocked(useRetryMeeting)
const mockedRetryEngagement = vi.mocked(useRetryEngagement)
const mockedMeetingSocket = vi.mocked(useMeetingSocket)

// mock persona data
const personaState: PersonaState = {
  engagementId: 'eng-1',
  trust: 80,
  interest: 80,
  patience: 80,
  disclosedFacts: [],
}

// create default meeting data
function makeMeeting(overrides: Partial<Meeting>): Meeting {
  return {
    id: 'meeting-1',
    engagementId: 'eng-1',
    personaId: 'persona-1',
    status: 'IN_PROGRESS',
    completedAt: null,
    transcriptStorageReference: null,
    completionOutcome: null,
    debriefFeedback: null,
    debriefTips: [],
    terminationReason: null,
    terminationMessage: null,
    meetingRetryAvailable: false,
    meetingRetriesRemaining: 0,
    behaviourLedger: [],
    ...overrides,
  }
}

// helper function to set up the mocked live meeting data for tests
function setup(meeting: Meeting, transcript: ConversationTurn[] = []) {
  mockedMeeting.mockReturnValue({
    data: meeting,
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useMeeting>)

  mockedTranscript.mockReturnValue({
    data: transcript,
    isLoading: false,
  } as unknown as ReturnType<typeof useMeetingTranscript>)

  mockedPersonaState.mockReturnValue({
    data: personaState,
    isLoading: false,
  } as unknown as ReturnType<typeof usePersonaState>)

  mockedResponseOptions.mockReturnValue({
    data: { available: false, options: [], interactionMode: 'FREEFORM', sourceSequence: 0, unavailableReason: null },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useMeetingResponseOptions>)

  mockedRetryMeeting.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useRetryMeeting>)

  mockedRetryEngagement.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useRetryEngagement>)

  mockedMeetingSocket.mockReturnValue({
    streamingText: '',
    isStreaming: false,
    error: null,
    personaState: null,
    latestSignals: [],
    termination: null,
    guidedOptionsPending: false,
    guidedOptionsError: null,
    behaviourFeedback: null,
    sendMessage: vi.fn(),
  } as unknown as ReturnType<typeof useMeetingSocket>)
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/engagements/eng-1/meetings/meeting-1']}>
      <Routes>
        <Route path="/dashboard/engagements/:engagementId/meetings/:meetingId" element={<LiveMeetingPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LiveMeetingPage states', () => {
  beforeEach(() => vi.clearAllMocks())

  // empty state when user has not 
  it('shows the empty-transcript placeholder when there are no turns yet', () => {
    setup(makeMeeting({ status: 'IN_PROGRESS' }), [])
    renderPage()

    expect(screen.getByText('Begin with a focused discovery question.')).toBeInTheDocument()
  })
})

describe('LiveMeetingPage status', () => {
  beforeEach(() => vi.clearAllMocks())

  it('replaces the transcript with a passed debrief and a Continue button', () => {
    setup(
      makeMeeting({
        status: 'COMPLETED',
        completionOutcome: 'PASSED',
        debriefFeedback: 'You navigated the discovery conversation well.',
        debriefTips: ['Keep connecting solutions back to stated priorities.'],
      }),
      [{ id: 't1', meetingId: 'meeting-1', actor: 'LEARNER', content: 'Hello', sequence: 0, signals: null, createdAt: '2026-08-01T10:00:00Z' }],
    )
    renderPage()

    expect(screen.getByText('Meeting passed')).toBeInTheDocument()
    expect(screen.getByText('You navigated the discovery conversation well.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue to Discovery Synthesis' })).toBeInTheDocument()

    // The prior transcript content must not still be rendered as a
    // competing panel alongside the debrief.
    expect(screen.queryByText('Hello')).not.toBeInTheDocument()
  })

  it('shows a failed debrief with a retry option instead of the live transcript', () => {
    setup(
      makeMeeting({
        status: 'COMPLETED',
        completionOutcome: 'FAILED',
        debriefFeedback: 'The relationship broke down before you reached a next step.',
        debriefTips: ['Address objections directly before moving on.'],
        meetingRetryAvailable: true,
        meetingRetriesRemaining: 2,
      }),
      [],
    )
    renderPage()

    expect(screen.getByText('Meeting not passed')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Retry live meeting \(2 remaining\)/ })).toBeInTheDocument()
    expect(screen.queryByText('Begin with a focused discovery question.')).not.toBeInTheDocument()
  })
})