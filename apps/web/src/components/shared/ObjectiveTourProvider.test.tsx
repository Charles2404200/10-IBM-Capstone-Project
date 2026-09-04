import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import type { StepType } from '@reactour/tour'
import ObjectiveTourProvider from './ObjectiveTourProvider'

const capturedSteps: StepType[][] = []

vi.mock('@reactour/tour', () => ({
  TourProvider: ({ children, steps }: { children: React.ReactNode; steps: StepType[] }) => {
    capturedSteps.push(steps)
    return <div data-testid="tour-provider">{children}</div>
  },
  useTour: () => ({
    setIsOpen: vi.fn(),
  }),
}))

vi.mock('./ObjectiveGuide', () => ({
  default: () => null,
}))

const OBJECTIVES = [
  { id: 'first', objective: 'First stop', description: 'Where you are.', targets: ['.first-target'] },
  { id: 'second', objective: 'Second stop', description: 'What to do.', targets: ['.second-target', '.also-second'] },
]

describe('ObjectiveTourProvider', () => {
  it('renders children successfully when an objective target is missing', () => {
    expect(() => {
      render(
        <ObjectiveTourProvider
          tourId="live-meeting"
          objectives={[
            {
              id: 'conditional',
              objective: 'Conditional target',
              description: 'May not exist.',
              targets: ['.conditional-target'],
            },
          ]}
        >
          <div>Meeting content</div>
        </ObjectiveTourProvider>,
      )
    }).not.toThrow()
  })

  it('renders the tour content normally when objective targets exist', () => {
    const { getByText } = render(
      <ObjectiveTourProvider
        tourId="live-meeting"
        objectives={[
          {
            id: 'meeting',
            objective: 'Meeting objective',
            description: 'Understand the meeting area.',
            targets: ['.meeting-target'],
          },
        ]}
      >
        <div className="meeting-target">Meeting content</div>
      </ObjectiveTourProvider>,
    )

    expect(getByText('Meeting content')).toBeInTheDocument()
  })

  /**
   * Step navigation is the library's job, but the order and anchoring it
   * navigates through are ours. A step that loses its selector sends the
   * learner to the wrong part of the screen.
   */
  it('builds one step per objective, in order, anchored to its first target', () => {
    capturedSteps.length = 0

    render(
      <ObjectiveTourProvider tourId="client-intelligence" objectives={OBJECTIVES}>
        <div />
      </ObjectiveTourProvider>,
    )

    const steps = capturedSteps.at(-1)!
    expect(steps).toHaveLength(2)
    expect(steps.map((step) => step.selector)).toEqual(['.first-target', '.second-target'])
    expect(steps[1].highlightedSelectors).toEqual(['.second-target', '.also-second'])
  })

  it('keeps the workspace usable behind the tour', () => {
    const { getByText, getByTestId } = render(
      <ObjectiveTourProvider tourId="outreach-workspace" objectives={OBJECTIVES}>
        <button type="button">Send outreach</button>
      </ObjectiveTourProvider>,
    )

    expect(getByTestId('tour-provider')).toContainElement(getByText('Send outreach'))
  })
})
