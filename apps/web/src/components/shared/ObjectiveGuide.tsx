import { useEffect, useRef } from 'react'
import { useTour } from '@reactour/tour'
import { useCompleteOnboarding } from '@/api/hooks/useAuth'
import { useAuthStore } from '@/store/authStore'

export default function ObjectiveGuide() {
  const { setIsOpen } = useTour()
  const onboardingRequired = useAuthStore((state) => state.onboardingRequired)
  const completeOnboarding = useCompleteOnboarding()
  const openedForCurrentVisit = useRef(false)

  useEffect(() => {
    if (!onboardingRequired || openedForCurrentVisit.current) {
      return
    }

    openedForCurrentVisit.current = true
    setIsOpen(true)
    completeOnboarding.mutate()
  }, [completeOnboarding, onboardingRequired, setIsOpen])

  return null
}
