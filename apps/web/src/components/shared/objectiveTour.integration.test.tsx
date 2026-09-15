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
    <ObjectiveTourProvider tourId="client-intelligence" objectives={OBJECTIVES}>
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

  it('opens on the first objective', () => {
    renderWorkspace()

    expect(screen.getByText('First stop')).toBeInTheDocument()
    expect(screen.queryByText('Second stop')).not.toBeInTheDocument()
  })

  it('walks forward and back through the steps', () => {
    renderWorkspace()

    fireEvent.click(screen.getByLabelText('Go to next step'))
    expect(screen.getByText('Second stop')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Go to prev step'))
    expect(screen.getByText('First stop')).toBeInTheDocument()
  })

  it('jumps to a step from the dots', () => {
    renderWorkspace()

    fireEvent.click(screen.getByLabelText('Go to step 2'))

    expect(screen.getByText('Second stop')).toBeInTheDocument()
  })

  it('leaves nothing over the workspace once closed', () => {
    const { container } = renderWorkspace()
    expect(container.ownerDocument.querySelector('.reactour__mask')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Close Tour'))

    expect(container.ownerDocument.querySelector('.reactour__mask')).not.toBeInTheDocument()
    expect(container.ownerDocument.querySelector('.reactour__popover')).not.toBeInTheDocument()
  })

  it('leaves the workspace controls working after the tour closes', () => {
    renderWorkspace()

    fireEvent.click(screen.getByLabelText('Close Tour'))
    fireEvent.click(screen.getByText('Submit hypothesis'))

    expect(onWorkspaceAction).toHaveBeenCalledOnce()
  })

  it('records the walkthrough when the learner closes it', () => {
    renderWorkspace()

    fireEvent.click(screen.getByLabelText('Close Tour'))

    expect(useTourProgressStore.getState().isComplete('user-1', 'client-intelligence')).toBe(true)
  })

  it('does not run again for a learner who has already been through it', () => {
    useTourProgressStore.getState().markComplete('user-1', 'client-intelligence')

    renderWorkspace()

    expect(screen.queryByText('First stop')).not.toBeInTheDocument()
    expect(document.querySelector('.reactour__mask')).not.toBeInTheDocument()
  })

  /**
   * The live meeting streams the client's reply in while the walkthrough may be
   * open over the top of it. The tour must not freeze that panel or swallow the
   * controls beside it, so this drives both while the mask is up.
   */
  it('lets the workspace keep updating and stay clickable while the tour is open', () => {
    function Streaming() {
      const [text, setText] = useState('Client is responding')
      return (
        <ObjectiveTourProvider tourId="live-meeting" objectives={OBJECTIVES}>
          <div className="step-one">{text}</div>
          <div className="step-two">Relationship</div>
          <button type="button" onClick={() => setText('Client has replied')}>Advance turn</button>
        </ObjectiveTourProvider>
      )
    }

    render(<Streaming />)
    expect(screen.getByText('First stop')).toBeInTheDocument()
    expect(document.querySelector('.reactour__mask')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Advance turn'))

    expect(screen.getByText('Client has replied')).toBeInTheDocument()
    expect(screen.getByText('First stop')).toBeInTheDocument()
  })

  /**
   * These workspaces are read on laptops and on half a screen, and the tour is
   * positioned by the library against the element it anchors to. A narrow
   * viewport must still find the anchors and must not push the page sideways.
   */
  it('still finds its anchors on a narrow viewport', () => {
    const original = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { value: 480, configurable: true })
    window.dispatchEvent(new Event('resize'))

    try {
      renderWorkspace()

      expect(screen.getByText('First stop')).toBeInTheDocument()
      expect(document.querySelector('.reactour__mask')).toBeInTheDocument()

      fireEvent.click(screen.getByLabelText('Go to next step'))
      expect(screen.getByText('Second stop')).toBeInTheDocument()

      expect(document.body.scrollWidth).toBeLessThanOrEqual(document.body.clientWidth || 480)
    } finally {
      Object.defineProperty(window, 'innerWidth', { value: original, configurable: true })
      window.dispatchEvent(new Event('resize'))
    }
  })

  /**
   * Some anchors sit on two forms of the same thing, one before an action and
   * one after — the outreach client panel becomes the client's reply once a
   * message has been sent. The step has to land on whichever is on the page.
   */
  it.each([
    ['before the attempt', 'Client signal'],
    ['after the attempt', 'What the client said'],
  ])('anchors to the client panel %s', (_when, label) => {
    render(
      <ObjectiveTourProvider
        tourId="outreach-workspace"
        objectives={[{ id: 'client', objective: 'Who you are writing to', description: 'One reader.', targets: ['.step-one'] }]}
      >
        <div className="step-one">{label}</div>
      </ObjectiveTourProvider>,
    )

    expect(screen.getByText('Who you are writing to')).toBeInTheDocument()
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  /**
   * A first engagement is the emptiest the research screen ever gets: no
   * evidence gathered, nothing on the board, the gate unmet. That is also the
   * only time the walkthrough runs, so the anchors have to survive it.
   */
  it('anchors to a section that is present but empty', () => {
    render(
      <ObjectiveTourProvider
        tourId="client-intelligence"
        objectives={[
          { id: 'board', objective: 'Build your evidence base', description: 'It collects here.', targets: ['.step-one'] },
          { id: 'gate', objective: 'Moving on', description: 'A floor, not a target.', targets: ['.step-two'] },
        ]}
      >
        <section className="step-one">Generate or add a source to begin building your evidence board.</section>
        <div className="step-two">Ready for Outreach?</div>
      </ObjectiveTourProvider>,
    )

    expect(screen.getByText('Build your evidence base')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Go to next step'))
    expect(screen.getByText('Moving on')).toBeInTheDocument()
  })
})
