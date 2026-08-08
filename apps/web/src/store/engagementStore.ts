import { create } from 'zustand'
import type { Engagement } from '@/api/types'

interface EngagementStore {
  activeEngagement: Engagement | null
  setActiveEngagement: (e: Engagement | null) => void
}

export const useEngagementStore = create<EngagementStore>((set) => ({
  activeEngagement: null,
  setActiveEngagement: (e) => set({ activeEngagement: e }),
}))
