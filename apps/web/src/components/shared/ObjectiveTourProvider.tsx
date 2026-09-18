import type { ReactNode } from 'react'
import { TourProvider, type StepType } from '@reactour/tour'
import ObjectiveGuide from './ObjectiveGuide'

interface Objective {
  id: string
  objective: string
  description: string
  targets: string[]
}

interface ObjectiveTour {
  tourId: string
  objectives: Objective[]
}

interface Props {
  tours: ObjectiveTour[]
  children: ReactNode
}

export default function ObjectiveTourProvider({ tours, children }: Props) {
  // Converts each objective into a Reactour step
  const stepsByTour = tours.reduce<Record<string, StepType[]>>((acc, tour) => {
    acc[tour.tourId] = tour.objectives.map((objective) => ({
      selector: objective.targets[0],
      highlightedSelectors: objective.targets,
      content: (
        <div>
          <strong>{objective.objective}</strong>
          <p style={{ marginTop: '0.75rem' }}>
            {objective.description}
          </p>
        </div>
      ),
    }))

    return acc
  }, {})

  return (
    <TourProvider steps={[]} showNavigation showPrevNextButtons showDots showCloseButton scrollSmooth
      styles={{
        popover: (base) => ({ ...base, borderRadius: 0, maxWidth: 360 }),
        maskArea: (base) => ({ ...base, rx: 4 }),
      }}
    >
      <ObjectiveGuide tours={tours} stepsByTour={stepsByTour} />
      {children}
    </TourProvider>
  )
}