import { useEffect } from 'react'
import { useTour } from '@reactour/tour'

const TOUR_VIEWED_KEY = 'tour-viewed'

function getViewedTours(): Record<string, boolean> {
  const stored = localStorage.getItem(TOUR_VIEWED_KEY)

  if (!stored) return {}

  try {
    return JSON.parse(stored)
  } catch {
    return {}
  }
}

export default function ObjectiveGuide({ tourId }: { tourId: string }) {
  const { setIsOpen } = useTour()

  useEffect(() => {
    const viewedTours = getViewedTours()

    if (viewedTours[tourId]) {
      return
    }

    viewedTours[tourId] = true
    localStorage.setItem( TOUR_VIEWED_KEY, JSON.stringify(viewedTours) )
    setIsOpen(true)
  }, [tourId, setIsOpen])

  return null
}