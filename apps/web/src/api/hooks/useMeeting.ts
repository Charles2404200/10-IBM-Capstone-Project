import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { Meeting, MeetingPreparation, ConversationTurn, PersonaState } from '@/api/types'

export const meetingKeys = {
  preparation: (engagementId: string) => ['meeting-preparation', engagementId] as const,
  meeting: (meetingId: string) => ['meeting', meetingId] as const,
  transcript: (meetingId: string) => ['meeting-transcript', meetingId] as const,
  personaState: (meetingId: string) => ['meeting-persona-state', meetingId] as const,
}

export function useMeetingPreparation(engagementId: string) {
  return useQuery({
    queryKey: meetingKeys.preparation(engagementId),
    queryFn: async () => {
      const res = await apiClient.get<MeetingPreparation>(
        `/api/v1/engagements/${engagementId}/preparation`
      )
      return res.data
    },
    enabled: Boolean(engagementId),
  })
}

export function useUpdateMeetingPreparation(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (data: { objective: string; agenda: string[]; discoveryQuestions: string[] }) => {
      const res = await apiClient.put<MeetingPreparation>(
        `/api/v1/engagements/${engagementId}/preparation`,
        data
      )
      return res.data
    },
    onSuccess: (preparation) => {
      // Retain the authoritative response in cache immediately. The editor owns
      // unsaved local draft state, so background query refreshes cannot erase it.
      qc.setQueryData(meetingKeys.preparation(engagementId), preparation)
      void qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}

export function useStartMeeting(engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<Meeting>(`/api/v1/engagements/${engagementId}/meetings`)
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['engagements', engagementId] }),
  })
}

export function useMeeting(meetingId: string) {
  return useQuery({
    queryKey: meetingKeys.meeting(meetingId),
    queryFn: async () => {
      const res = await apiClient.get<Meeting>(`/api/v1/meetings/${meetingId}`)
      return res.data
    },
    enabled: Boolean(meetingId),
  })
}

export function useMeetingTranscript(meetingId: string) {
  return useQuery({
    queryKey: meetingKeys.transcript(meetingId),
    queryFn: async () => {
      const res = await apiClient.get<ConversationTurn[]>(`/api/v1/meetings/${meetingId}/transcript`)
      return res.data
    },
    enabled: Boolean(meetingId),
  })
}

export function usePersonaState(meetingId: string) {
  return useQuery({
    queryKey: meetingKeys.personaState(meetingId),
    queryFn: async () => {
      const res = await apiClient.get<PersonaState>(`/api/v1/meetings/${meetingId}/persona-state`)
      return res.data
    },
    enabled: Boolean(meetingId),
  })
}

export function useCompleteMeeting(meetingId: string, engagementId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<Meeting>(`/api/v1/meetings/${meetingId}/complete`)
      return res.data
    },
    onSuccess: (meeting) => {
      qc.setQueryData(meetingKeys.meeting(meetingId), meeting)
      void qc.invalidateQueries({ queryKey: ['engagements', engagementId] })
    },
  })
}
