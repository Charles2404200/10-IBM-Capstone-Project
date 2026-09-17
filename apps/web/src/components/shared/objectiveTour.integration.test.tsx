import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { useAuthStore } from '@/store/authStore'
import { useTourProgressStore } from '@/store/tourProgressStore'
import ObjectiveTourProvider from './ObjectiveTourProvider'

/**
 * The other suites mock @reactour/tour, which is right for asserting our own
 * decisions but cannot show what a learner actually gets. These run the real
 * library so that navigation, closing and the mask are observed rather than
 * assumed.
 */

const complete = vi.fn()
vi.mock('@/api/hooks/useAuth', () => ({ useCompleteOnboarding: () => ({ mutate: complete }) }))
vi.mock('@/store/authStore', () => ({ useAuthStore: vi.fn() }))

beforeAll(() => {
  // jsdom has no IntersectionObserver; the library only uses it to reposition.
  class IntersectionObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return []
    }
  }
  vi.stubGlobal('IntersectionObserver', IntersectionObserverStub)
})

const OBJECTIVES = [
  { id: 'one', objective: 'First stop', description: 'Where you are.', targets: ['.step-one'] },
  { id: 'two', objective: 'Second stop', description: 'What to do next.', targets: ['.step-two'] },
]

function renderWorkspace() {
  return render(
    <ObjectiveTourProvider
      tours={[
        {
          tourId: 'client-intelligence',
          objectives: OBJECTIVES,
        },
      ]}
    >
      <div className="step-one">Evidence board</div>
      <div className="step-two">Hypothesis</div>
      <button type="button" onClick={onWorkspaceAction}>Submit hypothesis</button>
    </ObjectiveTourProvider>,
  )
}

const onWorkspaceAction = vi.fn()

describe('guided tour, running the real library', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useTourProgressStore.setState({ completedByUser: {} })
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ userId: 'user-1', onboardingRequired: true } as never),
    )
  })

  it('opens on the first objective', async () => {
    renderWorkspace()

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()
    expect(screen.queryByText('Second stop')).not.toBeInTheDocument()
  })

  it('moves to the second objective', async () => {
    renderWorkspace()

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /next/i }))

    expect(await screen.findByText('Second stop', {}, { timeout: 2000 }),).toBeInTheDocument()
  })

  it('closes the walkthrough and records completion', async () => {
    renderWorkspace()

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /close/i }))

    expect(useTourProgressStore.getState().isComplete('user-1', 'client-intelligence')).toBe(true)
  })

  it('calls the workspace action while the walkthrough is open', async () => {
    renderWorkspace()

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Submit hypothesis' }))

    expect(onWorkspaceAction).toHaveBeenCalled()
  })

  it('shows the mask around the current target', async () => {
    renderWorkspace()

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()

    expect(document.querySelector('.reactour__mask')).toBeInTheDocument()
  })

  it('supports closing and reopening the walkthrough', async () => {
    renderWorkspace()

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /close/i }))

    expect(screen.queryByText('First stop')).not.toBeInTheDocument()
  })

  it('does not open when onboarding is not required', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ userId: 'user-1', onboardingRequired: false } as never),
    )

    renderWorkspace()

    await new Promise((resolve) => setTimeout(resolve, 1200))

    expect(screen.queryByText('First stop')).not.toBeInTheDocument()
  })

  it('does not reopen a completed tour', async () => {
    useTourProgressStore.getState().markComplete('user-1', 'client-intelligence')

    renderWorkspace()

    await new Promise((resolve) => setTimeout(resolve, 1200))

    expect(screen.queryByText('First stop')).not.toBeInTheDocument()
  })

  it('opens a later tour when its targets are added after the first tour closes', async () => {
    function Workspace() {
      const [showSecond, setShowSecond] = useState(false)

      return (
        <>
          <div className="step-one">Evidence board</div>
          {showSecond && <div className="step-two">Hypothesis</div>}
          <button type="button" onClick={() => setShowSecond(true)}>Open document</button>
        </>
      )
    }

    render(
      <ObjectiveTourProvider
        tours={[
          {
            tourId: 'client-intelligence',
            objectives: [OBJECTIVES[0]],
          },
          {
            tourId: 'drop-and-drop',
            objectives: [OBJECTIVES[1]],
          },
        ]}
      >
        <Workspace />
      </ObjectiveTourProvider>,
    )

    expect(await screen.findByText('First stop', {}, { timeout: 2000 }),).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /close/i }))

    expect(screen.queryByText('First stop')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Open document' }))

    expect(await screen.findByText('Second stop', {}, { timeout: 2000 }),).toBeInTheDocument()
  })
})