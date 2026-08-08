import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Button,
  Tile,
  TextArea,
  InlineNotification,
} from '@carbon/react'
import { Send } from '@carbon/icons-react'
import { useMeeting, useMeetingTranscript, useCompleteMeeting } from '@/api/hooks/useMeeting'
import { useMeetingSocket } from '@/api/hooks/useMeetingSocket'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConversationTurn } from '@/api/types'

function RelationshipMeter({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p style={{ color: '#525252', fontSize: '0.75rem', marginBottom: '0.25rem' }}>
        {label} — {value}/100
      </p>
      <div style={{ background: '#e0e0e0', height: '6px', borderRadius: '3px' }}>
        <div
          style={{
            background: value >= 60 ? '#24a148' : value >= 30 ? '#f1c21b' : '#da1e28',
            width: `${value}%`,
            height: '100%',
            borderRadius: '3px',
            transition: 'width 0.3s ease',
          }}
        />
      </div>
    </div>
  )
}

function TurnBubble({ turn }: { turn: ConversationTurn }) {
  const isLearner = turn.actor === 'LEARNER'
  return (
    <div style={{ display: 'flex', justifyContent: isLearner ? 'flex-end' : 'flex-start' }}>
      <Tile
        style={{
          maxWidth: '70%',
          background: isLearner ? '#0f62fe' : '#f4f4f4',
          marginBottom: '0.75rem',
        }}
      >
        <p style={{ color: isLearner ? '#ffffff' : '#161616', whiteSpace: 'pre-wrap' }}>{turn.content}</p>
      </Tile>
    </div>
  )
}

export default function LiveMeetingPage() {
  const { engagementId, meetingId } = useParams<{ engagementId: string; meetingId: string }>()
  const navigate = useNavigate()
  const { data: meeting, isLoading: meetingLoading, isError: meetingError } = useMeeting(meetingId!)
  const { data: transcript, isLoading: transcriptLoading } = useMeetingTranscript(meetingId!)
  const completeMeeting = useCompleteMeeting(meetingId!, engagementId!)
  const { streamingText, isStreaming, error, personaState, sendMessage } = useMeetingSocket(meetingId!)

  const [message, setMessage] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [transcript, streamingText])

  if (meetingLoading || transcriptLoading) return <LoadingState />
  if (meetingError || !meeting) return <ErrorState />

  const isCompleted = meeting.status === 'COMPLETED'

  const handleSend = async () => {
    if (!message.trim() || isStreaming) return
    const outgoing = message
    setMessage('')
    await sendMessage(outgoing)
  }

  const handleComplete = () => {
    completeMeeting.mutate(undefined, {
      onSuccess: () => navigate(`/dashboard/engagements/${engagementId}/proposal`),
    })
  }

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={11} md={6} sm={4}>
        <Stack gap={5}>
          <Heading>Live Client Meeting</Heading>

          <div style={{ maxHeight: '55vh', overflowY: 'auto', padding: '0.5rem' }}>
            {transcript?.map((turn) => <TurnBubble key={turn.id} turn={turn} />)}
            {isStreaming && streamingText && (
              <TurnBubble
                turn={{
                  id: 'streaming',
                  meetingId: meetingId!,
                  actor: 'PERSONA',
                  content: streamingText,
                  sequence: -1,
                  signals: null,
                  createdAt: new Date().toISOString(),
                }}
              />
            )}
            <div ref={scrollRef} />
          </div>

          {error && <InlineNotification kind="error" title="Message failed" subtitle={error} hideCloseButton />}

          {!isCompleted && (
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end' }}>
              <div style={{ flex: 1 }}>
                <TextArea
                  id="message"
                  labelText=""
                  hideLabel
                  rows={2}
                  placeholder="Respond to the client…"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      handleSend()
                    }
                  }}
                />
              </div>
              <Button renderIcon={Send} disabled={isStreaming || !message.trim()} onClick={handleSend}>
                {isStreaming ? 'Sending…' : 'Send'}
              </Button>
            </div>
          )}

          {!isCompleted && (
            <Button kind="secondary" disabled={completeMeeting.isPending} onClick={handleComplete}>
              {completeMeeting.isPending ? 'Completing…' : 'Complete Meeting'}
            </Button>
          )}

          {isCompleted && (
            <InlineNotification
              kind="success"
              title="Meeting completed"
              subtitle="Head to the proposal studio to submit your proposal."
              hideCloseButton
            />
          )}
        </Stack>
      </Column>

      <Column lg={5} md={2} sm={4}>
        <Stack gap={4}>
          <h4 style={{ color: '#161616' }}>Relationship State</h4>
          <Tile>
            <Stack gap={4}>
              <RelationshipMeter label="Trust" value={personaState?.trust ?? 50} />
              <RelationshipMeter label="Interest" value={personaState?.interest ?? 50} />
              <RelationshipMeter label="Patience" value={personaState?.patience ?? 50} />
            </Stack>
          </Tile>
          {personaState && personaState.disclosedFacts.length > 0 && (
            <Tile>
              <Stack gap={2}>
                <h5 style={{ color: '#161616' }}>Facts Disclosed</h5>
                {personaState.disclosedFacts.map((fact) => (
                  <p key={fact} style={{ color: '#525252', fontSize: '0.875rem' }}>
                    • {fact}
                  </p>
                ))}
              </Stack>
            </Tile>
          )}
        </Stack>
      </Column>
    </Grid>
  )
}
