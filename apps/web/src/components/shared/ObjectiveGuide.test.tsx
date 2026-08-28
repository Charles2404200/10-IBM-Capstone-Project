import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import { useTour } from '@reactour/tour'
import { useCompleteOnboarding } from '@/api/hooks/useAuth'
import { useAuthStore } from '@/store/authStore'
import { useTourProgressStore } from '@/store/tourProgressStore'
import { TOUR_IDS } from '@/lifecycle/tours'
import ObjectiveGuide from './ObjectiveGuide'

vi.mock('@reactour/tour', () => ({ useTour: vi.fn() }))
vi.mock('@/api/hooks/useAuth', () => ({ useCompleteOnboarding: vi.fn() }))
vi.mock('@/store/authStore', () => ({ useAuthStore: vi.fn() }))

const setIsOpen = vi.fn()
const setSteps = vi.fn()
const complete = vi.fn()

const TOUR = 'client-intelligence'

const STEP = { selector: '.present-target', content: 'Present' }
const MISSING_STEP = { selector: '.absent-target', content: 'Absent' }

/** Puts an element on the page so a step's selector resolves. */
function renderTargets() {
  document.body.innerHTML = '<div class="present-target">target</div>'
}

function tour(isOpen: boolean, steps = [STEP]) {
  vi.mocked(useTour).mockReturnValue({ isOpen, setIsOpen, setSteps, steps } as unknown as ReturnType<typeof useTour>)
}

function signedInAs(userId: string | null, onboardingRequired: boolean) {
  vi.mocked(useAuthStore).mockImplementation((selector) =>
    selector({ userId, onboardingRequired } as never),
  )
}

/** Renders with the tour reported as open, then re-renders with it closed. */
function openThenClose(tourId = TOUR) {
  tour(true)
  const view = render(<ObjectiveGuide tourId={tourId} />)
  tour(false)
  view.rerender(<ObjectiveGuide tourId={tourId} />)
  return view
}

describe('ObjectiveGuide', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    renderTargets()
    useTourProgressStore.setState({ completedByUser: {} })
    tour(false)
    vi.mocked(useCompleteOnboarding).mockReturnValue({ mutate: complete } as never)
    signedInAs('user-1', true)
  })

  it('opens the walkthrough for a learner who is still being onboarded', () => {
    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setIsOpen).toHaveBeenCalledWith(true)
  })

  it('does not record completion merely because the walkthrough opened', () => {
    tour(true)

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(useTourProgressStore.getState().isComplete('user-1', TOUR)).toBe(false)
    expect(complete).not.toHaveBeenCalled()
  })

  it('records completion once the walkthrough closes', () => {
    openThenClose()

    expect(useTourProgressStore.getState().isComplete('user-1', TOUR)).toBe(true)
  })

  it('does not reopen a walkthrough the learner has already finished', () => {
    useTourProgressStore.getState().markComplete('user-1', TOUR)

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setIsOpen).not.toHaveBeenCalled()
  })

  it('still runs a walkthrough the learner has not reached yet', () => {
    useTourProgressStore.getState().markComplete('user-1', TOUR)

    render(<ObjectiveGuide tourId="outreach-workspace" />)

    expect(setIsOpen).toHaveBeenCalledWith(true)
  })

  it('leaves an interrupted walkthrough available, since it never closed', () => {
    tour(true)

    const { unmount } = render(<ObjectiveGuide tourId={TOUR} />)
    unmount()

    expect(useTourProgressStore.getState().isComplete('user-1', TOUR)).toBe(false)
  })

  it('does not open anything for a learner who has already been onboarded', () => {
    signedInAs('user-1', false)

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setIsOpen).not.toHaveBeenCalled()
  })

  it('records onboarding on the server only once every walkthrough is done', () => {
    TOUR_IDS.slice(0, -1).forEach((id) => useTourProgressStore.getState().markComplete('user-1', id))

    openThenClose(TOUR_IDS[TOUR_IDS.length - 1])

    expect(complete).toHaveBeenCalledOnce()
  })

  it('does not record onboarding on the server while walkthroughs remain', () => {
    openThenClose()

    expect(complete).not.toHaveBeenCalled()
  })

  it('keeps one learner\'s progress out of another\'s', () => {
    useTourProgressStore.getState().markComplete('user-2', TOUR)

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setIsOpen).toHaveBeenCalledWith(true)
  })

  it('drops steps whose target is not on the page this visit', () => {
    tour(false, [STEP, MISSING_STEP])

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setSteps).toHaveBeenCalledWith([STEP])
    expect(setIsOpen).toHaveBeenCalledWith(true)
  })

  it('leaves the step list alone when every target is present', () => {
    tour(false, [STEP])

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setSteps).not.toHaveBeenCalled()
  })

  it('stays closed, and available, when no target is on the page', () => {
    tour(false, [MISSING_STEP])

    render(<ObjectiveGuide tourId={TOUR} />)

    expect(setIsOpen).not.toHaveBeenCalled()
    expect(useTourProgressStore.getState().isComplete('user-1', TOUR)).toBe(false)
  })
})
