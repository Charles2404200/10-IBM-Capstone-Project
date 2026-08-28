import { useEffect, useRef } from 'react'
import { useTour } from '@reactour/tour'
import { useCompleteOnboarding } from '@/api/hooks/useAuth'
import { useAuthStore } from '@/store/authStore'
import { useTourProgressStore } from '@/store/tourProgressStore'
import { hasCompletedAllTours } from '@/lifecycle/tours'

/**
 * Runs a workspace walkthrough once per learner.
 *
 * Two things decide whether it opens. The account level flag says whether this
 * learner is being onboarded at all, so an account that predates onboarding
 * never sees a walkthrough. Per tour progress then says which of the
 * walkthroughs this particular learner has already been through, so arriving
 * at a workspace for the first time still explains it even after the earlier
 * ones are done.
 *
 * Completion is recorded when the tour closes rather than when it opens. A
 * learner who is interrupted half way through has not been onboarded, and
 * marking them as such on open is the difference between missing it once and
 * losing it permanently.
 */
export default function ObjectiveGuide({ tourId }: { tourId: string }) {
  const { isOpen, setIsOpen } = useTour()
  const userId = useAuthStore((state) => state.userId)
  const onboardingRequired = useAuthStore((state) => state.onboardingRequired)
  const isComplete = useTourProgressStore((state) => state.isComplete)
  const completedFor = useTourProgressStore((state) => state.completedFor)
  const markComplete = useTourProgressStore((state) => state.markComplete)
  const completeOnboarding = useCompleteOnboarding()

  const openedForCurrentVisit = useRef(false)
  const openedAtLeastOnce = useRef(false)

  useEffect(() => {
    if (!onboardingRequired || openedForCurrentVisit.current) {
      return
    }
    if (isComplete(userId, tourId)) {
      return
    }

    openedForCurrentVisit.current = true
    setIsOpen(true)
  }, [isComplete, onboardingRequired, setIsOpen, tourId, userId])

  // Finished, skipped and closed all arrive here as the same transition, and
  // all three mean the learner is done with this walkthrough.
  const completeMutation = completeOnboarding.mutate
  useEffect(() => {
    if (isOpen) {
      openedAtLeastOnce.current = true
      return
    }
    if (!openedAtLeastOnce.current) {
      return
    }

    openedAtLeastOnce.current = false
    markComplete(userId, tourId)

    if (hasCompletedAllTours(completedFor(userId))) {
      completeMutation()
    }
  }, [completeMutation, completedFor, isOpen, markComplete, tourId, userId])

  return null
}
