import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type {
  CreatePersonaRequest,
  CreateScenarioRequest,
  KnowledgeDocumentUploadRequest,
  GameplayDifficultyProfile,
  ScenarioSummary,
} from '@/api/types'

const adminScenarioKeys = {
  all: ['admin', 'scenarios'] as const,
}

/** Admin/author scenario authoring mutations — create scenario, add persona,
 *  publish/archive, set rubric weights, upload knowledge documents. */
export function useCreateScenario() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: CreateScenarioRequest) => {
      const res = await apiClient.post<ScenarioSummary>('/api/v1/admin/scenarios', request)
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminScenarioKeys.all })
      queryClient.invalidateQueries({ queryKey: ['scenarios'] })
    },
  })
}

export function useAddPersona(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: CreatePersonaRequest) => {
      const res = await apiClient.post<ScenarioSummary>(
        `/api/v1/admin/scenarios/${scenarioId}/personas`,
        request,
      )
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scenarios'] })
    },
  })
}

export function usePublishScenario() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (scenarioId: string) => {
      const res = await apiClient.patch<ScenarioSummary>(`/api/v1/admin/scenarios/${scenarioId}/publish`)
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  })
}

export function useArchiveScenario() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (scenarioId: string) => {
      const res = await apiClient.patch<ScenarioSummary>(`/api/v1/admin/scenarios/${scenarioId}/archive`)
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  })
}

export function useUpdateRubricWeights(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (weights: Record<string, number>) => {
      const res = await apiClient.put<ScenarioSummary>(`/api/v1/admin/scenarios/${scenarioId}/rubric`, { weights })
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  })
}

/** Updates runtime rules for future engagements only; active engagements use their snapshot. */
export function useUpdateGameplayDifficulty(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (profile: GameplayDifficultyProfile) => {
      const res = await apiClient.put<ScenarioSummary>(
        `/api/v1/admin/scenarios/${scenarioId}/difficulty-profile`,
        { profile },
      )
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminScenarioKeys.all })
      queryClient.invalidateQueries({ queryKey: ['scenarios'] })
    },
  })
}

export function useUploadKnowledgeDocument(scenarioId: string) {
  return useMutation({
    mutationFn: async (request: KnowledgeDocumentUploadRequest) => {
      const res = await apiClient.post<{ documentId: string }>(
        `/api/v1/admin/scenarios/${scenarioId}/documents`,
        request,
      )
      return res.data
    },
  })
}

/** All scenarios regardless of status (DRAFT/ACTIVE/ARCHIVED) for the admin builder. */
export function useAllScenariosForAdmin() {
  return useQuery({
    queryKey: adminScenarioKeys.all,
    queryFn: async () => {
      const res = await apiClient.get<ScenarioSummary[]>('/api/v1/admin/scenarios')
      return res.data
    },
  })
}
