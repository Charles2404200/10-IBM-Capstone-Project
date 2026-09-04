import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * Which walkthroughs each learner has already finished.
 *
 * Kept per user id rather than as one flag, so finishing the research
 * walkthrough does not silently consume the outreach one, and so two accounts
 * sharing a browser do not inherit each other's progress.
 *
 * Stored values arrive from localStorage and are therefore untrusted: a hand
 * edited or half written record must not take the guide down with it, so every
 * read narrows the value before using it.
 */
interface TourProgressState {
  completedByUser: Record<string, string[]>
  isComplete: (userId: string | null, tourId: string) => boolean
  completedFor: (userId: string | null) => string[]
  markComplete: (userId: string | null, tourId: string) => void
}

/** Narrows a stored entry to the shape the rest of the store assumes. */
function readList(state: TourProgressState, userId: string | null): string[] {
  if (!userId) return []
  const stored = state.completedByUser?.[userId]
  if (!Array.isArray(stored)) return []
  return stored.filter((id): id is string => typeof id === 'string')
}

export const useTourProgressStore = create<TourProgressState>()(
  persist(
    (set, get) => ({
      completedByUser: {},
      completedFor: (userId) => readList(get(), userId),
      isComplete: (userId, tourId) => readList(get(), userId).includes(tourId),
      markComplete: (userId, tourId) => {
        if (!userId) return
        set((state) => {
          const completed = readList(state, userId)
          if (completed.includes(tourId)) return state
          return {
            completedByUser: {
              ...(state.completedByUser ?? {}),
              [userId]: [...completed, tourId],
            },
          }
        })
      },
    }),
    {
      name: 'tour-progress',
      partialize: (state) => ({ completedByUser: state.completedByUser }),
    },
  ),
)
