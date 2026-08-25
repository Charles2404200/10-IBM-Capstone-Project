import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { TokenResponse } from '@/api/types'

interface AuthState {
  token: string | null
  userId: string | null
  displayName: string | null
  role: string | null
  onboardingRequired: boolean
  login: (response: TokenResponse) => void
  completeOnboarding: () => void
  logout: () => void
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      userId: null,
      displayName: null,
      role: null,
      onboardingRequired: false,
      login: (response) =>
        set({
          token: response.accessToken,
          userId: response.userId,
          displayName: response.displayName,
          role: response.role,
          onboardingRequired: response.onboardingRequired ?? false,
        }),
      completeOnboarding: () => set({ onboardingRequired: false }),
      logout: () => set({ token: null, userId: null, displayName: null, role: null, onboardingRequired: false }),
      isAuthenticated: () => Boolean(get().token),
    }),
    { name: 'auth-storage' }
  )
)
