import { useEffect, useRef } from 'react'
import { useTour, type StepType } from '@reactour/tour'
import { useCompleteOnboarding } from '@/api/hooks/useAuth'
import { useAuthStore } from '@/store/authStore'
import { useTourProgressStore } from '@/store/tourProgressStore'
import { hasCompletedAllTours } from '@/lifecycle/tours'

interface ObjectiveTour {
  tourId: string
  objectives: {
    id: string
    objective: string
    description: string
    targets: string[]
  }[]
}

interface Props {
  tours: ObjectiveTour[]
  stepsByTour: Record<string, StepType[]>
}

/**
 * Runs a workspace walkthrough once per learner, across as many sequential
 * tours as this page defines.
 *
 * The watcher (timeout + MutationObserver) is installed exactly once per
 * onboarding session and left running for the component's lifetime. It used
 * to be torn down and reinstalled every time `isOpen` changed, which meant it
 * silently died the moment the first tour opened and never came back — so a
 * second tour, whose target elements only appear after several user clicks,
 * was never discovered. Reading `isOpen`/`tours`/`stepsByTour` from refs lets
 * the same long-lived watcher always see current state without needing to be
 * recreated.
 */
export default function ObjectiveGuide({ tours, stepsByTour }: Props) {
  const { isOpen, setIsOpen, setSteps } = useTour()
  const userId = useAuthStore((state) => state.userId)
  const onboardingRequired = useAuthStore((state) => state.onboardingRequired)
  const isComplete = useTourProgressStore((state) => state.isComplete)
  const completedFor = useTourProgressStore((state) => state.completedFor)
  const markComplete = useTourProgressStore((state) => state.markComplete)
  const completeOnboarding = useCompleteOnboarding()

  const activeTourId = useRef<string | null>(null)
  const openedAtLeastOnce = useRef(false)
  const isOpenRef = useRef(isOpen)

  const toursRef = useRef(tours)
  const stepsByTourRef = useRef(stepsByTour)
  toursRef.current = tours
  stepsByTourRef.current = stepsByTour

  useEffect(() => {
    isOpenRef.current = isOpen
  }, [isOpen])

  useEffect(() => {
    if (!onboardingRequired) {
      return
    }

    const openAvailableTour = () => {
      if (isOpenRef.current || activeTourId.current) {
        return
      }

      const availableTour = toursRef.current.find((tour) => {
        if (isComplete(userId, tour.tourId)) {
          return false
        }

        const steps = stepsByTourRef.current[tour.tourId] ?? []

        return steps.some((step) =>
          typeof step.selector === 'string' ? Boolean(document.querySelector(step.selector)) : true,
        )
      })

      if (!availableTour) {
        return
      }

      const steps = stepsByTourRef.current[availableTour.tourId] ?? []

      // Workspaces render different sections at different stages, so a step can
      // point at something that is not on the page this time. Dropping those
      // steps is better than opening on an anchor that does not exist; if every
      // step is missing there is nothing to explain.
      const present = steps.filter((step) =>
        typeof step.selector === 'string' ? Boolean(document.querySelector(step.selector)) : true,
      )

      if (present.length === 0) {
        return
      }

      activeTourId.current = availableTour.tourId

      console.log('Opening tour:', availableTour.tourId)
      console.log('Steps:', present)

      setSteps(present)

      setTimeout(() => {
        console.log('Attempting to reopen tour:', availableTour.tourId)
        setIsOpen(true)
      }, 100)
    }

    const timeout = setTimeout(openAvailableTour, 1000)

    const observer = new MutationObserver(() => {
      openAvailableTour()
    })

    observer.observe(document.body, {
      childList: true,
      subtree: true,
    })

    return () => {
      clearTimeout(timeout)
      observer.disconnect()
    }
  }, [isComplete, onboardingRequired, setIsOpen, setSteps, userId])

  // Finished, skipped and closed all arrive here as the same transition, and
  // all three mean the learner is done with this walkthrough.
  const completeMutation = completeOnboarding.mutate
  useEffect(() => {
    if (isOpen) {
      openedAtLeastOnce.current = true
      return
    }

    if (!openedAtLeastOnce.current || !activeTourId.current) {
      return
    }

    openedAtLeastOnce.current = false

    const completedTourId = activeTourId.current
    activeTourId.current = null

    markComplete(userId, completedTourId)

    if (hasCompletedAllTours(completedFor(userId))) {
      completeMutation()
    }
  }, [completeMutation, completedFor, isOpen, markComplete, userId])

  return null
}