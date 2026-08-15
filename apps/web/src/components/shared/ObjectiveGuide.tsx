import { useEffect } from 'react'
import { useTour } from '@reactour/tour'
import { useMyEngagements } from '@/api/hooks/useEngagements'

export default function ObjectiveGuide() {
  const { setIsOpen } = useTour()
  const { data: engagements, isLoading } = useMyEngagements()
  const hasCompletedEngagement = engagements?.some((engagement) => engagement.completedAt !== null) ?? false

  useEffect(() => {
    if (!isLoading && !hasCompletedEngagement) {
      setIsOpen(true)
    }
  }, [isLoading, hasCompletedEngagement, setIsOpen])

  return null
}