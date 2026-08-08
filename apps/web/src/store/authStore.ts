import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { TokenResponse } from '@/api/types'

interface AuthState {
  token: string | null
  userId: string | null
  displayName: string | null
  role: string | null
  login: (response: TokenResponse) => void
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
      login: (response) =>
        set({
          token: response.accessToken,
          userId: response.userId,
          displayName: response.displayName,
          role: response.role,
        }),
      logout: () => set({ token: null, userId: null, displayName: null, role: null }),
      isAuthenticated: () => Boolean(get().token),
    }),
    { name: 'auth-storage' }
  )
)
