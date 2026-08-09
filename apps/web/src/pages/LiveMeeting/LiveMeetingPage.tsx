import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Column,
  Grid,
  Heading,
  InlineNotification,
  Stack,
  Tag,
  TextArea,
  Tile,
} from '@carbon/react'
import { ArrowRight, Send } from '@carbon/icons-react'
import { useCompleteMeeting, useMeeting, useMeetingTranscript } from '@/api/hooks/useMeeting'
import { useMeetingSocket } from '@/api/hooks/useMeetingSocket'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConversationTurn, PersonaState } from '@/api/types'
import styles from './LiveMeetingPage.module.scss'

const MEETING_THRESHOLD = 70

function RelationshipMeter({ label, value }: { label: string; value: number }) {
  const tone = value >= MEETING_THRESHOLD ? styles.meterPass : value >= 50 ? styles.meterWatch : styles.meterRisk
  return (
    <div className={styles.relationshipMetric}>
      <div>
        <span>{label}</span>
        <strong>{value}<small>/100</small></strong>
      </div>
      <div className={styles.meterTrack}><div className={tone} style={{ width: `${value}%` }} /></div>
      <p>{value >= MEETING_THRESHOLD ? 'Meeting threshold met' : `${MEETING_THRESHOLD - value} points to threshold`}</p>
    </div>
  )
}

function TurnBubble({ turn, isStreaming = false }: { turn: ConversationTurn; isStreaming?: boolean }) {
  const isLearner = turn.actor === 'LEARNER'
  return (
    <div className={isLearner ? styles.learnerTurn : styles.personaTurn}>
      <div className={`${styles.bubble} ${isLearner ? styles.learnerBubble : styles.personaBubble} ${isStreaming ? styles.streamingBubble : ''}`}>
        <p>{turn.content}</p>
      </div>
    </div>
  )
}

function deriveHint(transcript: ConversationTurn[], signals: string[], state: PersonaState) {
  const latestPersonaTurn = [...transcript].reverse().find((turn) => turn.actor === 'PERSONA')
  const question = latestPersonaTurn?.content.match(/[^?.!]*\?/)?.[0]?.trim()
  const signal = signals.find((item) => item.startsWith('objection:'))?.replace('objection:', '').trim()
  const guidance: string[] = []

  if (question) guidance.push(`Answer the client’s specific question: “${question}”`)
  else if (latestPersonaTurn) guidance.push('Acknowledge the client’s latest point before moving to your next question.')
  if (signal) guidance.push(`Address this concern directly: ${signal}`)
  if (state.patience < MEETING_THRESHOLD) guidance.push('Keep the next response focused: one point, one question.')
  if (state.trust < MEETING_THRESHOLD) guidance.push('Use a concrete detail from what the client has already shared.')
  if (state.interest < MEETING_THRESHOLD) guidance.push('Connect the next question to a business outcome the client cares about.')

  return guidance.slice(0, 3)
}

export default function LiveMeetingPage() {
  const { engagementId, meetingId } = useParams<{ engagementId: string; meetingId: string }>()
  const navigate = useNavigate()
  const { data: meeting, isLoading: meetingLoading, isError: meetingError } = useMeeting(meetingId!)
  const { data: transcript, isLoading: transcriptLoading } = useMeetingTranscript(meetingId!)
  const completeMeeting = useCompleteMeeting(meetingId!, engagementId!)
  const { streamingText, isStreaming, error, personaState, latestSignals, sendMessage } = useMeetingSocket(meetingId!)
  const [message, setMessage] = useState('')
  const [pendingMessage, setPendingMessage] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  const turns = transcript ?? []
  const currentState = personaState ?? { engagementId: engagementId ?? '', trust: 50, interest: 50, patience: 50, disclosedFacts: [] }
  const hint = useMemo(() => deriveHint(turns, latestSignals, currentState), [turns, latestSignals, currentState])

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [turns.length, streamingText])

  if (meetingLoading || transcriptLoading) return <LoadingState />
  if (meetingError || !meeting) return <ErrorState />

  const isCompleted = meeting.status === 'COMPLETED'
  const debriefTips = meeting.debriefTips ?? []
  const pendingIsPersisted = pendingMessage !== null
    && turns.some((turn) => turn.actor === 'LEARNER' && turn.content === pendingMessage)

  const handleSend = async () => {
    if (!message.trim() || isStreaming) return
    const outgoing = message.trim()
    setMessage('')
    setPendingMessage(outgoing)
    try {
      await sendMessage(outgoing)
    } finally {
      setPendingMessage(null)
    }
  }

  const handleComplete = () => {
    completeMeeting.mutate()
  }

  return (
    <div className={styles.page}>
      <Grid fullWidth className={styles.headerGrid}>
        <Column lg={16} md={8} sm={4}>
          <div className={styles.pageHeader}>
            <div>
              <p className={styles.eyebrow}>Live discovery</p>
              <Heading>Live Client Meeting</Heading>
            </div>
            {!isCompleted && (
              <Button kind="secondary" disabled={isStreaming || completeMeeting.isPending} onClick={handleComplete}>
                {completeMeeting.isPending ? 'Preparing debrief...' : 'End Meeting'}
              </Button>
            )}
          </div>
        </Column>
      </Grid>

      <Grid fullWidth className={styles.workspaceGrid}>
        <Column lg={11} md={8} sm={4}>
          <section className={styles.conversationPanel} aria-label="Live client conversation">
            <div className={styles.transcriptViewport}>
              {turns.length === 0 && <p className={styles.emptyTranscript}>Begin with a focused discovery question.</p>}
              {turns.map((turn) => <TurnBubble key={turn.id} turn={turn} />)}
              {pendingMessage && !pendingIsPersisted && (
                <TurnBubble turn={{ id: 'pending-learner', meetingId: meetingId!, actor: 'LEARNER', content: pendingMessage, sequence: -1, signals: null, createdAt: new Date().toISOString() }} />
              )}
              {isStreaming && streamingText && (
                <TurnBubble turn={{ id: 'streaming-persona', meetingId: meetingId!, actor: 'PERSONA', content: streamingText, sequence: -1, signals: null, createdAt: new Date().toISOString() }} isStreaming />
              )}
              <div ref={scrollRef} />
            </div>

            {error && <InlineNotification className={styles.errorNotification} kind="error" lowContrast title="Message failed" subtitle={error} hideCloseButton />}

            {!isCompleted && (
              <div className={styles.composer}>
                <TextArea
                  id="message"
                  labelText="Response"
                  hideLabel
                  rows={3}
                  placeholder="Respond to the client..."
                  value={message}
                  disabled={isStreaming}
                  onChange={(event) => setMessage(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' && !event.shiftKey) {
                      event.preventDefault()
                      void handleSend()
                    }
                  }}
                />
                <Button renderIcon={Send} disabled={isStreaming || !message.trim()} onClick={() => void handleSend()}>
                  {isStreaming ? 'Client is responding...' : 'Send'}
                </Button>
              </div>
            )}
          </section>

          {isCompleted && (
            <Tile className={meeting.completionOutcome === 'PASSED' ? styles.passedDebrief : styles.failedDebrief}>
              <Stack gap={4}>
                <div className={styles.debriefHeading}>
                  <div>
                    <p className={styles.eyebrow}>Meeting debrief</p>
                    <h2>{meeting.completionOutcome === 'PASSED' ? 'Meeting passed' : 'Meeting not passed'}</h2>
                  </div>
                  <Tag type={meeting.completionOutcome === 'PASSED' ? 'green' : 'red'}>{meeting.completionOutcome}</Tag>
                </div>
                <p className={styles.debriefFeedback}>{meeting.debriefFeedback}</p>
                {debriefTips.length > 0 && (
                  <ul className={styles.debriefTips}>
                    {debriefTips.map((tip) => <li key={tip}>{tip}</li>)}
                  </ul>
                )}
                {meeting.completionOutcome === 'PASSED' ? (
                  <Button renderIcon={ArrowRight} onClick={() => navigate(`/dashboard/engagements/${engagementId}/proposal`)}>
                    Continue to Discovery Synthesis
                  </Button>
                ) : (
                  <Button kind="secondary" onClick={() => navigate('/dashboard')}>Return to Command Centre</Button>
                )}
              </Stack>
            </Tile>
          )}
        </Column>

        <Column lg={5} md={8} sm={4}>
          <aside className={styles.decisionRail}>
            <section className={styles.relationshipPanel}>
              <div className={styles.railHeading}>
                <div>
                  <p className={styles.eyebrow}>Relationship state</p>
                  <h2>Meeting gate</h2>
                </div>
                <Tag type={currentState.trust >= MEETING_THRESHOLD && currentState.interest >= MEETING_THRESHOLD && currentState.patience >= MEETING_THRESHOLD ? 'green' : 'gray'}>
                  All metrics {MEETING_THRESHOLD}+
                </Tag>
              </div>
              <Stack gap={5}>
                <RelationshipMeter label="Trust" value={currentState.trust} />
                <RelationshipMeter label="Interest" value={currentState.interest} />
                <RelationshipMeter label="Patience" value={currentState.patience} />
              </Stack>
            </section>

            {!isCompleted && hint.length > 0 && (
              <Tile className={styles.hintPanel}>
                <p className={styles.eyebrow}>Response-based hint</p>
                <h3>Focus your next turn</h3>
                <ul>{hint.map((item) => <li key={item}>{item}</li>)}</ul>
              </Tile>
            )}

            {currentState.disclosedFacts.length > 0 && (
              <Tile className={styles.factsPanel}>
                <p className={styles.eyebrow}>Validated during meeting</p>
                <h3>Facts disclosed</h3>
                <ul>{currentState.disclosedFacts.map((fact) => <li key={fact}>{fact.replace(/_/g, ' ')}</li>)}</ul>
              </Tile>
            )}
          </aside>
        </Column>
      </Grid>
    </div>
  )
}
