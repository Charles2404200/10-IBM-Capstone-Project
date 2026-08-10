/**
 * Client-side state for the game layer.
 *
 * Everything here is presentation state — where the avatar is standing, whether
 * the intro has been seen, audio preferences. Simulation truth (phase, trust,
 * interest, patience, scores) stays on the server and arrives through the
 * existing react-query hooks; this store never duplicates it.
 *
 * Persisted to localStorage so a refresh does not drop the learner back in the
 * lobby or replay the intro at them.
 */
import { create } from 'zustand'
import { DEFAULT_AUDIO_SETTINGS, type AudioSettings } from '../audio/engine'

const STORAGE_KEY = 'ibm-sim.game.v1'

export interface GamePreferences {
  /** Master switch for the pixel layer. Off = the original Carbon-only app. */
  worldEnabled: boolean
  /** Honours prefers-reduced-motion, but can also be set explicitly. */
  reducedMotion: boolean
  audio: AudioSettings
}

interface PersistedShape {
  preferences: GamePreferences
  onboardingComplete: boolean
  avatar: AvatarChoice
  visitedStations: string[]
}

export interface AvatarChoice {
  displayName: string
  /** Palette substitutions applied to the base consultant sprite. */
  hair: string
  suit: string
  skin: string
}

export const AVATAR_PRESETS: ReadonlyArray<{ id: string; label: string } & AvatarChoice> = [
  { id: 'navy', label: 'Navy suit', displayName: '', hair: 'h', suit: 'b', skin: 's' },
  { id: 'charcoal', label: 'Charcoal suit', displayName: '', hair: 'H', suit: 'd', skin: 'S' },
  { id: 'teal', label: 'Teal jacket', displayName: '', hair: 'z', suit: 'm', skin: 't' },
  { id: 'aubergine', label: 'Aubergine suit', displayName: '', hair: 'N', suit: 'p', skin: 'T' },
]

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

const DEFAULTS: PersistedShape = {
  preferences: {
    worldEnabled: true,
    reducedMotion: false,
    audio: { ...DEFAULT_AUDIO_SETTINGS },
  },
  onboardingComplete: false,
  avatar: { displayName: '', hair: 'h', suit: 'b', skin: 's' },
  visitedStations: [],
}

function load(): PersistedShape {
  if (typeof window === 'undefined') return DEFAULTS
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return {
        ...DEFAULTS,
        preferences: { ...DEFAULTS.preferences, reducedMotion: prefersReducedMotion() },
      }
    }
    const parsed = JSON.parse(raw) as Partial<PersistedShape>
    return {
      preferences: { ...DEFAULTS.preferences, ...parsed.preferences },
      onboardingComplete: parsed.onboardingComplete ?? false,
      avatar: { ...DEFAULTS.avatar, ...parsed.avatar },
      visitedStations: parsed.visitedStations ?? [],
    }
  } catch {
    // A corrupt preferences blob must never stop the app booting.
    return DEFAULTS
  }
}

function persist(state: PersistedShape): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // Private-mode / quota failures are not worth surfacing.
  }
}

interface GameStore extends PersistedShape {
  setPreferences: (next: Partial<GamePreferences>) => void
  setAudio: (next: Partial<AudioSettings>) => void
  completeOnboarding: (avatar: AvatarChoice) => void
  resetOnboarding: () => void
  markVisited: (glyph: string) => void
  hasVisited: (glyph: string) => boolean
}

export const useGameStore = create<GameStore>((set, get) => ({
  ...load(),

  setPreferences: (next) =>
    set((state) => {
      const preferences = { ...state.preferences, ...next }
      persist({ ...state, preferences })
      return { preferences }
    }),

  setAudio: (next) =>
    set((state) => {
      const preferences = { ...state.preferences, audio: { ...state.preferences.audio, ...next } }
      persist({ ...state, preferences })
      return { preferences }
    }),

  completeOnboarding: (avatar) =>
    set((state) => {
      const updated = { ...state, avatar, onboardingComplete: true }
      persist(updated)
      return { avatar, onboardingComplete: true }
    }),

  resetOnboarding: () =>
    set((state) => {
      const updated = { ...state, onboardingComplete: false }
      persist(updated)
      return { onboardingComplete: false }
    }),

  markVisited: (glyph) =>
    set((state) => {
      if (state.visitedStations.includes(glyph)) return {}
      const visitedStations = [...state.visitedStations, glyph]
      persist({ ...state, visitedStations })
      return { visitedStations }
    }),

  hasVisited: (glyph) => get().visitedStations.includes(glyph),
}))
