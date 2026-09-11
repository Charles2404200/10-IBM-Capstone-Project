import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MeetingPreparationPage from './MeetingPreparationPage'
import {
  useMeetingPreparation,
  useStartMeeting,
  useUpdateMeetingPreparation,
} from '@/api/hooks/useMeeting'
import type { MeetingPreparation } from '@/api/types'

vi.mock('@/api/hooks/useMeeting', () => ({
  useMeetingPreparation: vi.fn(),
  useUpdateMeetingPreparation: vi.fn(),
  useStartMeeting: vi.fn(),
}))
vi.mock('@/components/shared/ObjectiveTourProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))
vi.mock('@/components/shared/LoadingState', () => ({ default: () => <div>Loading...</div> }))
vi.mock('@/components/shared/ErrorState', () => ({ default: () => <div>Error...</div> }))

// mocks matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// typed mock references
const mockedPreparation = vi.mocked(useMeetingPreparation)
const mockedUpdatePreparation = vi.mocked(useUpdateMeetingPreparation)
const mockedStartMeeting = vi.mocked(useStartMeeting)

// helper function to set up the mocked meeting preparation data for tests
function setup(preparation: Partial<MeetingPreparation>) {
  mockedPreparation.mockReturnValue({
    data: {
      id: 'prep-1',
      engagementId: 'eng-1',
      objective: '',
      agenda: [],
      discoveryQuestions: [],
      readinessScore: 0,
      ready: false,
      ...preparation,
    } as MeetingPreparation,
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useMeetingPreparation>)

  mockedUpdatePreparation.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useUpdateMeetingPreparation>)

  mockedStartMeeting.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useStartMeeting>)
}

// renders the meeting preparation page with the required router
function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/engagements/eng-1/preparation']}>
      <Routes>
        <Route
          path="/dashboard/engagements/:engagementId/preparation"
          element={<MeetingPreparationPage />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('MeetingPreparationPage readiness labels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
  })

  it('reports agenda readiness out of 3 with zero items', () => {
    setup({ agenda: [], discoveryQuestions: [] })
    renderPage()

    // readiness should use the new threshold of 3 for both agenda items and questions
    expect(screen.getByText('0/3 items')).toBeInTheDocument()
    expect(screen.getByText('0/3 questions')).toBeInTheDocument()
  })

  it('marks agenda readiness complete once exactly 3 non-empty agenda items exist', () => {
    setup({
      agenda: ['Intro', 'Discovery', 'Next steps'],
      discoveryQuestions: [],
    })
    renderPage()

    // three non-empty agenda items should satisfy the new readiness threshold
    expect(screen.getByText('3/3 items')).toBeInTheDocument()
  })

  it('allows adding a 4th agenda item, over cap', async () => {
    const user = userEvent.setup()
    setup({
      agenda: ['Intro', 'Discovery', 'Next steps'],
      discoveryQuestions: [],
    })
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Add agenda item' }))

    // the editor should still allow additional agenda items beyond the minimum readiness threshold
    expect(screen.getByLabelText('Agenda item 4')).toBeInTheDocument()
  })
})