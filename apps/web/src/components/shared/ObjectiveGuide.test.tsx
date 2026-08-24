import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import { useTour } from '@reactour/tour'
import ObjectiveGuide from './ObjectiveGuide'

vi.mock('@reactour/tour', () => ({ useTour: vi.fn() }))

const setIsOpen = vi.fn()

describe('ObjectiveGuide', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()

    vi.mocked(useTour).mockReturnValue({
      setIsOpen,
    } as unknown as ReturnType<typeof useTour>)
  })

  it('opens tour for a first-time user', () => {
    render(<ObjectiveGuide tourId="client-intelligence" />)
    expect(setIsOpen).toHaveBeenCalledWith(true)
  })

  it('tour is set as viewed when first opened', () => {
    render(<ObjectiveGuide tourId="client-intelligence" />)
    expect(JSON.parse(localStorage.getItem('tour-viewed') ?? '{}')).toEqual({ 'client-intelligence': true })
  })

  it('does not open a tour that has already been viewed', () => {
    // sets local storage key as viewed for client intelligence before test
    localStorage.setItem(
      'tour-viewed',
      JSON.stringify({ 'client-intelligence': true })
    )

    render(<ObjectiveGuide tourId="client-intelligence" />)
    expect(setIsOpen).not.toHaveBeenCalled()
  })

  it('tour does not reopen after it has been viewed even if it was closed halfway through', () => {
    // render tour and ensure tour was opened
    const { unmount } = render(<ObjectiveGuide tourId="live-meeting" />)
    expect(setIsOpen).toHaveBeenCalledWith(true)

    // remove component to simulate the user leaving the page
    unmount()

    setIsOpen.mockClear()

    // render tour to indicate return to page and ensure tour is not opened
    render(<ObjectiveGuide tourId="live-meeting" />)
    expect(setIsOpen).not.toHaveBeenCalled()
  })

  it('keeps viewed state for different pages independently', () => {
    localStorage.setItem(
      'tour-viewed',
      JSON.stringify({ 'client-intelligence': true }),
    )

    // checks a different page to ensure tour opens
    render(<ObjectiveGuide tourId="live-meeting" />)
    expect(setIsOpen).toHaveBeenCalledWith(true)
    expect(JSON.parse(localStorage.getItem('tour-viewed') ?? '{}')).toEqual({
      'client-intelligence': true,
      'live-meeting': true,
    })
  })
})