import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Client, type IMessage } from '@stomp/stompjs'
import { useAuthStore } from '@/store/authStore'
import { meetingKeys } from '@/api/hooks/useMeeting'
import type { ConversationTurn, Meeting, MeetingTermination, MeetingTurnResult, PersonaState } from '@/api/types'

interface UsePersonaTurnStreamResult {
  streamingText: string
  isStreaming: boolean
  error: string | null
  personaState: PersonaState | null
  latestSignals: string[]
  termination: MeetingTermination | null
  sendMessage: (message: string) => Promise<void>
}

type SocketEvent =
  | { type: 'turn.thinking'; payload: { status: string } }
  | { type: 'turn.delta'; payload: { text: string } }
  | { type: 'turn.complete'; payload: MeetingTurnResult }
  | { type: 'turn.error'; payload: { message: string } }

function toWebSocketUrl(baseUrl: string): string {
  const url = new URL('/ws', baseUrl || window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

/**
 * Real-time replacement for the SSE-based {@code usePersonaTurnStream}: a single
 * persistent STOMP-over-WebSocket connection per meeting, instead of one HTTP
 * POST per message. Exposes the exact same external shape so
 * `LiveMeetingPage` only needs an import swap.
 *
 * Auth works around the browser limitation that neither `WebSocket` nor
 * `EventSource` can carry a custom `Authorization` header on their handshake
 * request: the JWT is instead sent as a STOMP `CONNECT` frame header (once the
 * socket is already open), which the backend's `StompAuthChannelInterceptor`
 * validates before allowing any further STOMP commands.
 */
export function useMeetingSocket(meetingId: string): UsePersonaTurnStreamResult {
  const [streamingText, setStreamingText] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [personaState, setPersonaState] = useState<PersonaState | null>(null)
  const [latestSignals, setLatestSignals] = useState<string[]>([])
  const [termination, setTermination] = useState<MeetingTermination | null>(null)
  const qc = useQueryClient()
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

  const clientRef = useRef<Client | null>(null)
  const connectedRef = useRef(false)
  // Guards against a rapid double Enter/click firing two publishes before the
  // first reply's `turn.complete` arrives — same synchronous-ref rationale as
  // the SSE hook (state updates aren't visible until next render).
  const sendingRef = useRef(false)
  const pendingResolversRef = useRef<{ resolve: () => void; reject: (e: Error) => void } | null>(null)

  useEffect(() => {
    const token = useAuthStore.getState().token
    const client = new Client({
      brokerURL: toWebSocketUrl(baseUrl),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    })

    client.onConnect = () => {
      connectedRef.current = true
      client.subscribe(`/topic/meetings/${meetingId}`, (frame: IMessage) => {
        const event = JSON.parse(frame.body) as SocketEvent
        if (event.type === 'turn.thinking') {
          setStreamingText('Client is reading your message...')
        } else if (event.type === 'turn.delta') {
          setStreamingText(event.payload.text)
        } else if (event.type === 'turn.complete') {
          const result = event.payload
          qc.setQueryData<ConversationTurn[]>(meetingKeys.transcript(meetingId), (current = []) => {
            const knownIds = new Set(current.map((turn) => turn.id))
            const newTurns = [result.learnerTurn, result.personaTurn].filter((turn) => !knownIds.has(turn.id))
            return [...current, ...newTurns].sort((left, right) => left.sequence - right.sequence)
          })
          setPersonaState(result.personaState)
          qc.setQueryData(meetingKeys.personaState(meetingId), result.personaState)
          if (result.responseOptions) {
            qc.setQueryData(meetingKeys.responseOptions(meetingId), result.responseOptions)
          } else {
            void qc.invalidateQueries({ queryKey: meetingKeys.responseOptions(meetingId) })
          }
          setLatestSignals(result.meetingSignals ?? [])
          const termination = result.termination
          setTermination(termination ?? null)
          if (termination) {
            qc.setQueryData<Meeting>(meetingKeys.meeting(meetingId), (current) => current ? {
              ...current,
              status: 'COMPLETED',
              completedAt: new Date().toISOString(),
              completionOutcome: 'FAILED',
              debriefFeedback: termination.message,
              debriefTips: termination.retryGuidance,
              terminationReason: termination.reason,
              terminationMessage: termination.message,
              meetingRetryAvailable: termination.meetingRetryAvailable,
              meetingRetriesRemaining: termination.meetingRetriesRemaining,
            } : current)
          }
          setStreamingText('')
          setIsStreaming(false)
          sendingRef.current = false
          pendingResolversRef.current?.resolve()
          pendingResolversRef.current = null
        } else if (event.type === 'turn.error') {
          setError(event.payload.message)
          setIsStreaming(false)
          sendingRef.current = false
          pendingResolversRef.current?.reject(new Error(event.payload.message))
          pendingResolversRef.current = null
        }
      })
    }

    client.onStompError = (frame) => {
      setError(frame.headers.message ?? 'WebSocket connection error')
      pendingResolversRef.current?.reject(new Error(frame.headers.message ?? 'WebSocket connection error'))
      pendingResolversRef.current = null
    }
    client.onWebSocketClose = () => {
      connectedRef.current = false
    }

    client.activate()
    clientRef.current = client

    return () => {
      connectedRef.current = false
      client.deactivate()
      clientRef.current = null
    }
  }, [meetingId, baseUrl, qc])

  const sendMessage = useCallback(
    async (message: string) => {
      if (sendingRef.current) return
      const client = clientRef.current
      if (!client || !connectedRef.current) {
        setError('Not connected to the live meeting channel yet — please retry in a moment')
        return
      }

      sendingRef.current = true
      setIsStreaming(true)
      setError(null)
      setStreamingText('Sending to client...')

      // Same idempotency key pattern as the SSE hook: the backend replays an
      // already-persisted turn for a repeated messageId instead of re-invoking
      // the AI (P0 fix — duplicate reply incident).
      const messageId = crypto.randomUUID()

      try {
        await new Promise<void>((resolve, reject) => {
          pendingResolversRef.current = { resolve, reject }
          client.publish({
            destination: `/app/meetings/${meetingId}/send`,
            body: JSON.stringify({ message, messageId }),
          })
        })
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to send message')
      } finally {
        setIsStreaming(false)
        sendingRef.current = false
      }
    },
    [meetingId]
  )

  return { streamingText, isStreaming, error, personaState, latestSignals, termination, sendMessage }
}
