import type { PropsWithChildren } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '@/api/client'
import { useScenarioLifecycle, useUpdateScenarioLifecycle } from './useAdminScenarios'
import type { ScenarioLifecycleResponse } from '@/api/types'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), put: vi.fn() },
}))

const response: ScenarioLifecycleResponse = {
  version: 4,
  definition: { schemaVersion: 1, stages: [], objectives: [] },
}

function harness() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, wrapper }
}

describe('scenario lifecycle authoring hooks', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads the saved lifecycle definition and version from the dedicated endpoint', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: response })
    const { wrapper } = harness()

    const { result } = renderHook(() => useScenarioLifecycle('scenario-1'), { wrapper })

    await waitFor(() => expect(result.current.data).toEqual(response))
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/admin/scenarios/scenario-1/lifecycle')
  })

  it('saves the draft with its optimistic-lock version and invalidates authoring caches', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: { ...response, version: 5 } })
    const { client, wrapper } = harness()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useUpdateScenarioLifecycle('scenario-1'), { wrapper })

    await result.current.mutateAsync(response)

    expect(apiClient.put).toHaveBeenCalledWith('/api/v1/admin/scenarios/scenario-1/lifecycle', response)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'scenarios', 'scenario-1', 'authoring'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['scenarios'] })
  })

  it('refetches the latest lifecycle after a conflict without replacing mutation variables', async () => {
    const conflict = { response: { status: 409 } }
    vi.mocked(apiClient.put).mockRejectedValue(conflict)
    const { client, wrapper } = harness()
    const refetch = vi.spyOn(client, 'refetchQueries').mockResolvedValue(undefined)
    const { result } = renderHook(() => useUpdateScenarioLifecycle('scenario-1'), { wrapper })

    await expect(result.current.mutateAsync(response)).rejects.toBe(conflict)

    await waitFor(() => expect(result.current.variables).toEqual(response))
    expect(refetch).toHaveBeenCalledWith({ queryKey: ['admin', 'scenarios', 'scenario-1', 'lifecycle'] })
  })
})
