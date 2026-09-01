import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import apiClient from '@/api/client'
import { adminPlatformKeys } from '@/api/hooks/useAdminPlatformOverview'
import type {
  CreatePersonaRequest,
  CreateScenarioRequest,
  KnowledgeDocumentUploadRequest,
  GameplayDifficultyProfile,
  LeadAuthoringRequest,
  LeadAuthoringView,
  LeadSummary,
  ScenarioAuthoringConfig,
  ScenarioAuthoringView,
  ScenarioCatalogPage,
  ScenarioSummary,
  UpdateScenarioBlueprintRequest,
  KnowledgeDocumentSummary,
  KnowledgeDocumentUpdateRequest,
} from '@/api/types'

const adminScenarioKeys = {
  all: ['admin', 'scenarios'] as const,
  catalog: (filters: AdminScenarioCatalogFilters) => ['admin', 'scenarios', 'catalog', filters] as const,
}

export interface AdminScenarioCatalogFilters {
  search?: string
  status?: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  page: number
  size: number
}

async function fetchAdminScenarioCatalog(filters: AdminScenarioCatalogFilters) {
  const response = await apiClient.get<ScenarioCatalogPage>('/api/v1/admin/scenarios/catalog', { params: filters })
  return response.data
}

/**
 * Bounded authoring library query. Keeping this separate from the legacy full
 * list endpoint lets existing integrations continue unchanged while authors
 * work fluidly with thousands of scenarios.
 */
export function useAdminScenarioCatalog(filters: AdminScenarioCatalogFilters, enabled = true) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: adminScenarioKeys.catalog(filters),
    queryFn: () => fetchAdminScenarioCatalog(filters),
    placeholderData: keepPreviousData,
    staleTime: 45_000,
    refetchOnWindowFocus: false,
    enabled,
  })

  useEffect(() => {
    if (!query.data || filters.page + 1 >= query.data.totalPages) return
    const nextFilters = { ...filters, page: filters.page + 1 }
    void queryClient.prefetchQuery({
      queryKey: adminScenarioKeys.catalog(nextFilters),
      queryFn: () => fetchAdminScenarioCatalog(nextFilters),
      staleTime: 45_000,
    })
  }, [filters, query.data, queryClient])

  return query
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
      queryClient.invalidateQueries({ queryKey: adminPlatformKeys.overview })
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
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function usePublishScenario() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (scenarioId: string) => {
      const res = await apiClient.patch<ScenarioSummary>(`/api/v1/admin/scenarios/${scenarioId}/publish`)
      return res.data
    },
    onSuccess: (_, scenarioId) => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useArchiveScenario() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (scenarioId: string) => {
      const res = await apiClient.patch<ScenarioSummary>(`/api/v1/admin/scenarios/${scenarioId}/archive`)
      return res.data
    },
    onSuccess: (_, scenarioId) => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useUpdateRubricWeights(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (weights: Record<string, number>) => {
      const res = await apiClient.put<ScenarioSummary>(`/api/v1/admin/scenarios/${scenarioId}/rubric`, { weights })
      return res.data
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
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
      queryClient.invalidateQueries({ queryKey: adminPlatformKeys.overview })
    },
  })
}

export function useUploadKnowledgeDocument(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: KnowledgeDocumentUploadRequest) => {
      const res = await apiClient.post<{ documentId: string }>(
        `/api/v1/admin/scenarios/${scenarioId}/documents`,
        request,
      )
      return res.data
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

/** All scenarios regardless of status (DRAFT/ACTIVE/ARCHIVED) for the admin builder. */
export function useAllScenariosForAdmin(enabled = true) {
  return useQuery({
    queryKey: adminScenarioKeys.all,
    queryFn: async () => {
      const res = await apiClient.get<ScenarioSummary[]>('/api/v1/admin/scenarios')
      return res.data
    },
    enabled,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  })
}

export function useScenarioAuthoring(scenarioId: string) {
  return useQuery({
    queryKey: [...adminScenarioKeys.all, scenarioId, 'authoring'],
    queryFn: async () => {
      const res = await apiClient.get<ScenarioAuthoringView>(`/api/v1/admin/scenarios/${scenarioId}/authoring`)
      return res.data
    },
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    enabled: Boolean(scenarioId),
  })
}

function invalidateScenarioAuthoring(queryClient: ReturnType<typeof useQueryClient>, scenarioId: string) {
  queryClient.invalidateQueries({ queryKey: adminScenarioKeys.all })
  queryClient.invalidateQueries({ queryKey: [...adminScenarioKeys.all, scenarioId, 'authoring'] })
  queryClient.invalidateQueries({ queryKey: ['scenarios'] })
  queryClient.invalidateQueries({ queryKey: ['leads', scenarioId] })
  queryClient.invalidateQueries({ queryKey: [...adminScenarioKeys.all, scenarioId, 'leads'] })
  queryClient.invalidateQueries({ queryKey: adminPlatformKeys.overview })
  queryClient.invalidateQueries({ queryKey: [...adminScenarioKeys.all, scenarioId, 'documents'] })
}

export function useUpdateScenarioBlueprint(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: UpdateScenarioBlueprintRequest) => {
      const res = await apiClient.put<ScenarioAuthoringView>(`/api/v1/admin/scenarios/${scenarioId}/blueprint`, request)
      return res.data
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useUpdateScenarioAuthoringConfig(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (config: ScenarioAuthoringConfig) => {
      const res = await apiClient.put<ScenarioAuthoringView>(`/api/v1/admin/scenarios/${scenarioId}/authoring-config`, { config })
      return res.data
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useCreateScenarioRevision(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<ScenarioAuthoringView>(`/api/v1/admin/scenarios/${scenarioId}/revisions`)
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminScenarioKeys.all })
      queryClient.invalidateQueries({ queryKey: adminPlatformKeys.overview })
    },
  })
}

export function useCreateScenarioLead(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: LeadAuthoringRequest) => {
      const res = await apiClient.post<LeadSummary>(`/api/v1/admin/scenarios/${scenarioId}/leads`, request)
      return res.data
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useScenarioAuthoringLeads(scenarioId: string) {
  return useQuery({
    queryKey: [...adminScenarioKeys.all, scenarioId, 'leads'],
    queryFn: async () => {
      const res = await apiClient.get<LeadAuthoringView[]>(`/api/v1/admin/scenarios/${scenarioId}/leads`)
      return res.data
    },
    enabled: Boolean(scenarioId),
  })
}

export function useUpdateScenarioLead(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ leadId, request }: { leadId: string; request: LeadAuthoringRequest }) => {
      const res = await apiClient.put<LeadSummary>(`/api/v1/admin/scenarios/${scenarioId}/leads/${leadId}`, request)
      return res.data
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useDeleteScenarioLead(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (leadId: string) => apiClient.delete(`/api/v1/admin/scenarios/${scenarioId}/leads/${leadId}`),
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

/*Get knowledge documents by scenarioID */
export function useGetKnowledgeDocuments(scenarioId: string) {
  return useQuery({
    queryKey: [...adminScenarioKeys.all, scenarioId, 'documents'],
    queryFn: async () => {
      const res = await apiClient.get<KnowledgeDocumentSummary[]>(
        `/api/v1/admin/scenarios/${scenarioId}/documents`,
      )
      return res.data
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    enabled: Boolean(scenarioId),
  })
}

export function useDeleteKnowledgeDocument(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (documentId: string) => {
      await apiClient.delete(`/api/v1/admin/scenarios/${scenarioId}/documents/${documentId}`)
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}

export function useUpdateKnowledgeDocument(scenarioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ documentId, ...request }: { documentId: string } & KnowledgeDocumentUpdateRequest) => {
      await apiClient.put(`/api/v1/admin/scenarios/${scenarioId}/documents/${documentId}`, request)
    },
    onSuccess: () => invalidateScenarioAuthoring(queryClient, scenarioId),
  })
}