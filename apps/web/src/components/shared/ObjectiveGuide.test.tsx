import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import { useTour } from '@reactour/tour'
import { useCompleteOnboarding } from '@/api/hooks/useAuth'
import { useAuthStore } from '@/store/authStore'
import ObjectiveGuide from './ObjectiveGuide'

vi.mock('@reactour/tour', () => ({ useTour: vi.fn() }))
vi.mock('@/api/hooks/useAuth', () => ({ useCompleteOnboarding: vi.fn() }))
vi.mock('@/store/authStore', () => ({ useAuthStore: vi.fn() }))

const setIsOpen = vi.fn()
const complete = vi.fn()

describe('ObjectiveGuide', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useTour).mockReturnValue({
      setIsOpen,
    } as unknown as ReturnType<typeof useTour>)
    vi.mocked(useCompleteOnboarding).mockReturnValue({ mutate: complete } as never)
  })

  it('opens and persists the guide for a newly registered learner', () => {
    vi.mocked(useAuthStore).mockImplementation((selector) => selector({ onboardingRequired: true } as never))

    render(<ObjectiveGuide />)

    expect(setIsOpen).toHaveBeenCalledWith(true)
    expect(complete).toHaveBeenCalledOnce()
  })

  it('does not reopen the guide for an existing learner', () => {
    vi.mocked(useAuthStore).mockImplementation((selector) => selector({ onboardingRequired: false } as never))

    render(<ObjectiveGuide />)

    expect(setIsOpen).not.toHaveBeenCalled()
    expect(complete).not.toHaveBeenCalled()
  })
})
