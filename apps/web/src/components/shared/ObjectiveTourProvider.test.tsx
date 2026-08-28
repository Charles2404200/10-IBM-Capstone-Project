import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import ObjectiveTourProvider from './ObjectiveTourProvider'

vi.mock('@reactour/tour', () => ({
  TourProvider: ({ children }: { children: React.ReactNode }) => <div data-testid="tour-provider">{children}</div>,
  useTour: () => ({
    setIsOpen: vi.fn(),
  }),
}))

vi.mock('./ObjectiveGuide', () => ({
  default: () => null,
}))

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
})
