import { useEffect } from 'react'
import { useTour } from '@reactour/tour'

export default function ObjectiveGuide() {
  const { setIsOpen } = useTour()

  useEffect(() => {
    setIsOpen(true)
  }, [setIsOpen])

  return null
}