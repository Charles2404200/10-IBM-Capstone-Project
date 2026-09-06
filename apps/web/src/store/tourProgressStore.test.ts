import { beforeEach, describe, expect, it } from 'vitest'
import { useTourProgressStore } from './tourProgressStore'

const STORAGE_KEY = 'tour-progress'

const reset = () => useTourProgressStore.setState({ completedByUser: {} })

describe('tourProgressStore', () => {
  beforeEach(reset)

  it('records a completed walkthrough for one learner', () => {
    const { markComplete, isComplete } = useTourProgressStore.getState()

    markComplete('user-1', 'client-intelligence')

    expect(isComplete('user-1', 'client-intelligence')).toBe(true)
  })

  it('leaves other walkthroughs available once one is complete', () => {
    const { markComplete, isComplete } = useTourProgressStore.getState()

    markComplete('user-1', 'client-intelligence')

    expect(isComplete('user-1', 'outreach-workspace')).toBe(false)
  })

  it('keeps progress isolated between learners sharing a browser', () => {
    const { markComplete, isComplete } = useTourProgressStore.getState()

    markComplete('user-1', 'client-intelligence')

    expect(isComplete('user-2', 'client-intelligence')).toBe(false)
  })

  it('records a walkthrough once, however many times it closes', () => {
    const { markComplete, completedFor } = useTourProgressStore.getState()

    markComplete('user-1', 'live-meeting')
    markComplete('user-1', 'live-meeting')

    expect(completedFor('user-1')).toEqual(['live-meeting'])
  })

  it('treats a signed-out learner as having no progress and records nothing', () => {
    const { markComplete, isComplete, completedFor } = useTourProgressStore.getState()

    markComplete(null, 'live-meeting')

    expect(isComplete(null, 'live-meeting')).toBe(false)
    expect(completedFor(null)).toEqual([])
  })

  it('survives a corrupted stored record rather than throwing', () => {
    useTourProgressStore.setState({
      completedByUser: { 'user-1': 'not-an-array' } as unknown as Record<string, string[]>,
    })
    const { isComplete, completedFor, markComplete } = useTourProgressStore.getState()

    expect(isComplete('user-1', 'live-meeting')).toBe(false)
    expect(completedFor('user-1')).toEqual([])

    markComplete('user-1', 'live-meeting')

    expect(useTourProgressStore.getState().completedFor('user-1')).toEqual(['live-meeting'])
  })

  /**
   * The state has to come back after a refresh, not just after a re-render —
   * that is the whole point of persisting it.
   */
  it('reads progress back from storage after a reload', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ state: { completedByUser: { 'user-1': ['live-meeting'] } }, version: 0 }),
    )

    useTourProgressStore.persist.rehydrate()

    expect(useTourProgressStore.getState().isComplete('user-1', 'live-meeting')).toBe(true)
    expect(useTourProgressStore.getState().isComplete('user-1', 'proposal-studio')).toBe(false)
  })

  it('writes progress to storage so it outlives the page', () => {
    useTourProgressStore.getState().markComplete('user-1', 'outreach-workspace')

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')
    expect(stored.state.completedByUser['user-1']).toContain('outreach-workspace')
  })
})
