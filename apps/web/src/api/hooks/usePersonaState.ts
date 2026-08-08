import { useCallback, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { meetingKeys } from '@/api/hooks/useMeeting'
import type { MeetingTurnResult, PersonaState } from '@/api/types'

interface UsePersonaTurnStreamResult {
  streamingText: string
  isStreaming: boolean
  error: string | null
  personaState: PersonaState | null
  sendMessage: (message: string) => Promise<void>
}

/**
 * Consumes the SSE endpoint POST /meetings/{id}/messages. A plain EventSource
 * cannot be used because it neither supports POST bodies nor custom
 * Authorization headers, so this hook reads the response body as a stream via
 * fetch and parses Server-Sent Event frames manually.
 */
export function usePersonaTurnStream(meetingId: string): UsePersonaTurnStreamResult {
  const [streamingText, setStreamingText] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [personaState, setPersonaState] = useState<PersonaState | null>(null)
  const qc = useQueryClient()
  const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  const tokenRef = useRef<string | null>(null)
  tokenRef.current = useAuthStore.getState().token
  // Synchronous guard against double-submit: React state updates (isStreaming)
  // are not visible until the next render, so a rapid double Enter/click in the
  // same tick could pass the `isStreaming` check twice before the first render
  // flush. A ref is set the instant sendMessage starts, closing that race
  // (P0 fix — duplicate reply incident).
  const sendingRef = useRef(false)

  const sendMessage = useCallback(
    async (message: string) => {
      if (sendingRef.current) return
      sendingRef.current = true
      setIsStreaming(true)
      setError(null)
      setStreamingText('')

      // Idempotency key: if this request is retried (network hiccup, accidental
      // double dispatch), the backend recognises the same messageId and replays
      // the already-generated persona reply instead of calling the AI again
      // (P0 fix — duplicate reply incident).
      const messageId = crypto.randomUUID()

      try {
        const response = await fetch(`${baseUrl}/api/v1/meetings/${meetingId}/messages`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            Authorization: tokenRef.current ? `Bearer ${tokenRef.current}` : '',
          },
          body: JSON.stringify({ message, messageId }),
        })

        if (!response.ok || !response.body) {
          throw new Error(`Meeting turn request failed with status ${response.status}`)
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          const frames = buffer.split('\n\n')
          buffer = frames.pop() ?? ''

          for (const frame of frames) {
            const eventName = frame.match(/^event:\s*(.+)$/m)?.[1]?.trim()
            const dataLine = frame.match(/^data:\s*(.+)$/m)?.[1]?.trim()
            if (!dataLine) continue
            const payload = JSON.parse(dataLine)

            if (eventName === 'turn.delta') {
              setStreamingText(payload.text)
            } else if (eventName === 'turn.complete') {
              const result = payload as MeetingTurnResult
              setPersonaState(result.personaState)
              // Stop showing the streaming overlay bubble as soon as the final
              // turn arrives, instead of waiting for the SSE reader loop to
              // fully close below. Previously isStreaming only flipped false in
              // the `finally` block after this loop ended, which could race
              // with the transcript refetch settling first and render both the
              // overlay bubble and the persisted transcript bubble for the same
              // reply at once (P0 fix — visible "duplicate" persona message).
              setStreamingText('')
              setIsStreaming(false)
              sendingRef.current = false
              qc.invalidateQueries({ queryKey: meetingKeys.transcript(meetingId) })
            }
          }
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to send message')
      } finally {
        setIsStreaming(false)
        sendingRef.current = false
      }
    },
    [baseUrl, meetingId, qc]
  )

  return { streamingText, isStreaming, error, personaState, sendMessage }
}
