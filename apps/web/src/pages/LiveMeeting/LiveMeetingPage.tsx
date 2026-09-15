import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Column,
  Grid,
  Heading,
  InlineLoading,
  InlineNotification,
  Modal,
  Stack,
  Tag,
  TextArea,
  Tile,
} from '@carbon/react'
import { ArrowRight, Idea, Send } from '@carbon/icons-react'
import { useMeeting, useMeetingResponseOptions, useMeetingTranscript, usePersonaState, useRetryMeeting } from '@/api/hooks/useMeeting'
import { useRetryEngagement } from '@/api/hooks/useEngagements'
import { useMeetingSocket } from '@/api/hooks/useMeetingSocket'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConversationTurn, MeetingBehaviourFeedback, MeetingTermination, PersonaState } from '@/api/types'
import styles from './LiveMeetingPage.module.scss'
import ObjectiveTourProvider from '@/components/shared/ObjectiveTourProvider'

const DEFAULT_MEETING_THRESHOLD = 70

const LIVE_MEETING_OBJECTIVES = [
  {
    id: 'relationship',
    objective: 'Understand relationship state',
    description: 'This shows your current relationship state with the client and your goal metrics before you can move to the debrief and proposal stage.',
    targets: ['.objective-relationship'],
  },
  {
    id: 'meeting-options',
    objective: 'Determine your responses',
    description: 'This is the area where your meeting will take place. You will either have the choice to choose a generated response or type in your response here, depending on the difficulty of this engagement.',
    targets: ['.objective-meeting-view'],
  },
  {
    id: 'hints',
    objective: 'Meeting hints',
    description: 'Pay attention to this whole section, as it may provide useful hints to guide an appropriate response to the client.',
    targets: ['.objective-hints'],
  },
]

function RelationshipMeter({ label, value, threshold }: { label: string; value: number; threshold: number }) {
  const tone = value >= threshold ? styles.meterPass : value >= 50 ? styles.meterWatch : styles.meterRisk
  return (
    <div className={styles.relationshipMetric}>
      <div>
        <span>{label}</span>
        <strong>{value}<small>/100</small></strong>
      </div>
      <div className={styles.meterTrack}><div className={tone} style={{ width: `${value}%` }} /></div>
      <p>{value >= threshold ? 'Meeting threshold met' : `${threshold - value} points to threshold`}</p>
    </div>
  )
}

function scoreDelta(value: number) {
  return value > 0 ? `+${value}` : `${value}`
}

function BehaviourFeedback({ feedback }: { feedback: MeetingBehaviourFeedback }) {
  const positive = feedback.trustDelta >= 0 && feedback.interestDelta >= 0 && feedback.patienceDelta >= 0
  return (
    <section className={`${styles.behaviourPanel} ${positive ? styles.behaviourPositive : styles.behaviourRecovery}`}>
      <p className={styles.eyebrow}>Simulation Director</p>
      <div className={styles.behaviourHeading}>
        <h3>{feedback.quality.replaceAll('_', ' ').toLowerCase()}</h3>
        <div className={styles.behaviourDeltas} aria-label="Relationship impact">
          <span>Trust {scoreDelta(feedback.trustDelta)}</span>
          <span>Interest {scoreDelta(feedback.interestDelta)}</span>
          <span>Patience {scoreDelta(feedback.patienceDelta)}</span>
        </div>
      </div>
      <p>{feedback.explanation}</p>
      {feedback.verifiedBehaviours.length > 0 && (
        <div className={styles.behaviourTags}>
          {feedback.verifiedBehaviours.map((behaviour) => <Tag key={behaviour} type="blue">{behaviour.replaceAll('_', ' ')}</Tag>)}
        </div>
      )}
      <div className={styles.behaviourNextAction}>
        <strong>Next best action</strong>
        <span>{feedback.nextBestAction}</span>
      </div>
    </section>
  )
}

function MeetingIntelligence({
  feedback,
  disclosedFacts,
  readyToClose,
}: {
  feedback: MeetingBehaviourFeedback | null
  disclosedFacts: string[]
  readyToClose: boolean
}) {
  return (
    <Tile className={styles.liveIntelligencePanel}>
      {disclosedFacts.length > 0 && (
        <section className={styles.validatedFacts}>
          <p className={styles.eyebrow}>Validated during meeting</p>
          <h3>Facts disclosed</h3>
          <ul>{disclosedFacts.map((fact) => <li key={fact}>{fact.replace(/_/g, ' ')}</li>)}</ul>
        </section>
      )}
      {readyToClose && (
        <section className={styles.readyToClosePanel}>
          <p className={styles.eyebrow}>Client readiness</p>
          <h3>Ready to conclude</h3>
          <p>The client has enough confidence to move forward. Confirm the agreed next step; the next client response will close the meeting automatically.</p>
        </section>
      )}
      {feedback && <BehaviourFeedback feedback={feedback} />}
    </Tile>
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

function deriveHint(transcript: ConversationTurn[], signals: string[], state: PersonaState, threshold: number) {
  const latestPersonaTurn = [...transcript].reverse().find((turn) => turn.actor === 'PERSONA')
  const question = latestPersonaTurn?.content.match(/[^?.!]*\?/)?.[0]?.trim()
  const signal = signals.find((item) => item.startsWith('objection:'))?.replace('objection:', '').trim()
  const guidance: string[] = []

  if (question) guidance.push(`Answer the client’s specific question: “${question}”`)
  else if (latestPersonaTurn) guidance.push('Acknowledge the client’s latest point before moving to your next question.')
  if (signal) guidance.push(`Address this concern directly: ${signal}`)
  if (state.patience < threshold) guidance.push('Keep the next response focused: one point, one question.')
  if (state.trust < threshold) guidance.push('Use a concrete detail from what the client has already shared.')
  if (state.interest < threshold) guidance.push('Connect the next question to a business outcome the client cares about.')

  return guidance.slice(0, 3)
}

function toTermination(meetingReason: string | null, message: string | null, tips: string[],
                       meetingRetryAvailable: boolean, meetingRetriesRemaining: number): MeetingTermination | null {
  if (!meetingReason || !message) return null
  if (meetingReason !== 'UNPROFESSIONAL_CONDUCT' && meetingReason !== 'RELATIONSHIP_THRESHOLD_BREACH') return null
  return { reason: meetingReason, message, retryGuidance: tips, meetingRetryAvailable, meetingRetriesRemaining }
}

export default function LiveMeetingPage() {
  const { engagementId, meetingId } = useParams<{ engagementId: string; meetingId: string }>()
  const navigate = useNavigate()
  const { data: meeting, isLoading: meetingLoading, isError: meetingError } = useMeeting(meetingId!)
  const { data: transcript, isLoading: transcriptLoading } = useMeetingTranscript(meetingId!)
  const { data: persistedPersonaState, isLoading: personaStateLoading } = usePersonaState(meetingId!)
  const { data: responseOptions, isLoading: responseOptionsLoading, isError: responseOptionsError, refetch: refetchResponseOptions } = useMeetingResponseOptions(
    meetingId!,
    meeting?.status === 'IN_PROGRESS' && meeting.interactionMode !== 'FREEFORM',
  )
  const retryMeeting = useRetryMeeting(meetingId!, engagementId!)
  const { streamingText, isStreaming, error, personaState, latestSignals, termination, guidedOptionsPending, guidedOptionsError, behaviourFeedback, sendMessage } = useMeetingSocket(meetingId!)
  const retryEngagement = useRetryEngagement(engagementId!)
  const [message, setMessage] = useState('')
  const [pendingMessage, setPendingMessage] = useState<string | null>(null)
  const [terminationDismissed, setTerminationDismissed] = useState(false)
  const [hintOpen, setHintOpen] = useState(false)
  const transcriptRef = useRef<HTMLDivElement>(null)

  const turns = useMemo(() => transcript ?? [], [transcript])
  const currentState = personaState ?? persistedPersonaState
  const latestPersistedFeedback = meeting?.behaviourLedger?.[meeting.behaviourLedger.length - 1] ?? null
  const currentBehaviourFeedback = behaviourFeedback ?? latestPersistedFeedback
  const meetingThreshold = meeting?.meetingThreshold ?? DEFAULT_MEETING_THRESHOLD
  const hint = useMemo(
    () => currentState ? deriveHint(turns, latestSignals, currentState, meetingThreshold) : [],
    [turns, latestSignals, currentState, meetingThreshold]
  )

  useEffect(() => {
    const transcriptViewport = transcriptRef.current
    if (!transcriptViewport) return

    transcriptViewport.scrollTo({
      top: transcriptViewport.scrollHeight,
      behavior: 'smooth',
    })
  }, [turns.length, streamingText])

  useEffect(() => {
    setMessage('')
    setPendingMessage(null)
    setTerminationDismissed(false)
    setHintOpen(false)
  }, [meetingId])

  if (meetingLoading || transcriptLoading || personaStateLoading) return <LoadingState />
  if (meetingError || !meeting) return <ErrorState />
  if (!currentState) return <ErrorState />

  const isCompleted = meeting.status === 'COMPLETED'
  const isFreeformMeeting = meeting.interactionMode === 'FREEFORM'
  const isGuidedMeeting = !isFreeformMeeting
  const debriefTips = meeting.debriefTips ?? []
  const automaticTermination = termination ?? toTermination(
    meeting.terminationReason,
    meeting.terminationMessage,
    debriefTips,
    meeting.meetingRetryAvailable,
    meeting.meetingRetriesRemaining
  )
  const canRetryMeeting = automaticTermination?.meetingRetryAvailable ?? meeting.meetingRetryAvailable
  const meetingRetriesRemaining = automaticTermination?.meetingRetriesRemaining ?? meeting.meetingRetriesRemaining
  const meetingGateMet = currentState.trust >= meetingThreshold
    && currentState.interest >= meetingThreshold && currentState.patience >= meetingThreshold
  const clientReadyToClose = latestSignals.includes('client_ready_to_close')
    || latestSignals.includes('client_committed_next_step')
  const pendingIsPersisted = pendingMessage !== null
    && turns.some((turn) => turn.actor === 'LEARNER' && turn.content === pendingMessage)

  const sendResponse = async (outgoing: string) => {
    if (!outgoing || isStreaming) return
    setMessage('')
    setPendingMessage(outgoing)
    try {
      await sendMessage(outgoing)
    } finally {
      setPendingMessage(null)
    }
  }

  const handleSend = async () => sendResponse(message.trim())

  const handleRetryLead = () => {
    retryEngagement.mutate(undefined, {
      onSuccess: (retry) => navigate(`/dashboard/engagements/${retry.id}/intelligence`),
    })
  }

  const handleRetryMeeting = () => {
    retryMeeting.mutate(undefined, {
      onSuccess: (retry) => navigate(`/dashboard/engagements/${engagementId}/meetings/${retry.id}`),
    })
  }

  return (
    <ObjectiveTourProvider tourId="live-meeting" objectives={LIVE_MEETING_OBJECTIVES}>
    <div className={`${styles.page} ${isCompleted ? styles.completedPage : ''}`}>
      <Grid fullWidth className={styles.headerGrid}>
        <Column lg={16} md={8} sm={4}>
          <div className={styles.pageHeader}>
            <div>
              <p className={styles.eyebrow}>Live discovery</p>
              <Heading>Live Client Meeting</Heading>
            </div>
          </div>
        </Column>
      </Grid>

      <Grid fullWidth className={styles.workspaceGrid}>
        <Column lg={11} md={8} sm={4} className={styles.conversationColumn}>
          <section className={`${styles.conversationPanel} objective-meeting-view`} aria-label="Live client conversation">
            {!isCompleted && hint.length > 0 && (
              <div className={styles.meetingHint}>
                <Button
                  kind="ghost"
                  size="sm"
                  renderIcon={Idea}
                  iconDescription="Show response hint"
                  className={styles.hintTrigger}
                  aria-expanded={hintOpen}
                  aria-controls="meeting-response-hint"
                  onClick={() => setHintOpen((open) => !open)}
                >
                  Stuck? Get a hint
                </Button>
                {hintOpen && (
                  <section className={styles.hintPopover} id="meeting-response-hint" aria-label="Response hint">
                    <p className={styles.eyebrow}>Next-turn hint</p>
                    <p>{hint[0]}</p>
                  </section>
                )}
              </div>
            )}
            <div className={styles.transcriptViewport} ref={transcriptRef}>
              {turns.length === 0 && <p className={styles.emptyTranscript}>Begin with a focused discovery question.</p>}
              {turns.map((turn) => <TurnBubble key={turn.id} turn={turn} />)}
              {pendingMessage && !pendingIsPersisted && (
                <TurnBubble turn={{ id: 'pending-learner', meetingId: meetingId!, actor: 'LEARNER', content: pendingMessage, sequence: -1, signals: null, createdAt: new Date().toISOString() }} />
              )}
              {isStreaming && streamingText && (
                <TurnBubble turn={{ id: 'streaming-persona', meetingId: meetingId!, actor: 'PERSONA', content: streamingText, sequence: -1, signals: null, createdAt: new Date().toISOString() }} isStreaming />
              )}
            </div>

            {error && <InlineNotification className={styles.errorNotification} kind="error" lowContrast title="Message failed" subtitle={error} hideCloseButton />}

            {!isCompleted && isGuidedMeeting && (responseOptionsLoading || responseOptionsError || responseOptions?.interactionMode === 'GUIDED') && (
              <section className={styles.guidedComposer} aria-label="Guided response choices">
                <div className={styles.guidedHeading}>
                  <div>
                    <p className={styles.eyebrow}>Guided response</p>
                    <h3>{meetingGateMet ? 'Confirm the agreed next step' : 'Choose your next response'}</h3>
                  </div>
                  <Tag type="blue">Three options</Tag>
                </div>
                <p className={styles.guidedDescription}>
                  {meetingGateMet
                    ? 'The client is ready to conclude. Confirm one concrete next step; the meeting will then close automatically.'
                    : 'Choose carefully: not every professional-sounding response advances the conversation. Its impact is evaluated from the actual conversation.'}
                </p>
                {responseOptionsLoading && <InlineLoading description="Preparing response options..." />}
                {(isStreaming || guidedOptionsPending) && <InlineLoading description={isStreaming ? 'Client is responding...' : 'Preparing next response options...'} />}
                {!isStreaming && !guidedOptionsPending && !responseOptionsLoading && responseOptions?.available && (
                  <div className={styles.responseChoices}>
                    {responseOptions.options.map((option, index) => (
                      <button
                        className={styles.responseChoice}
                        disabled={isStreaming}
                        key={`${responseOptions.sourceSequence}-${index}`}
                        onClick={() => void sendResponse(option)}
                        type="button"
                      >
                        <span className={styles.choiceNumber}>Option {index + 1}</span>
                        <span className={styles.choiceContent}>{option}</span>
                        <ArrowRight className={styles.choiceIcon} size={20} aria-hidden="true" />
                      </button>
                    ))}
                  </div>
                )}
                {!isStreaming && !guidedOptionsPending && !responseOptionsLoading && (!responseOptions?.available || responseOptionsError || (guidedOptionsError && !responseOptions?.available)) && (
                  <div className={styles.responseOptionsUnavailable}>
                    <InlineNotification
                      kind="warning"
                      lowContrast
                      hideCloseButton
                      title="Response options are unavailable"
                      subtitle={guidedOptionsError ?? responseOptions?.unavailableReason ?? 'Please try again to generate grounded response options.'}
                    />
                    <Button kind="tertiary" size="sm" onClick={() => void refetchResponseOptions()}>Try again</Button>
                  </div>
                )}
              </section>
            )}

            {!isCompleted && isFreeformMeeting && (
              <section className={styles.freeformComposer} aria-label="Freeform meeting response">
                <div className={styles.freeformHeading}>
                  <div>
                    <p className={styles.eyebrow}>Hard mode · live dialogue</p>
                    <h3>{meetingGateMet ? 'Bring the conversation to a natural close' : 'Respond in your own words'}</h3>
                  </div>
                  <Tag type="purple">Freeform</Tag>
                </div>
                <p>{meetingGateMet
                  ? 'The client is ready to wrap up. Confirm the shared next step, owner and timing in one concise response.'
                  : 'Listen carefully, address the client’s actual concern, and move the conversation forward without scripted choices.'}</p>
                <div className={styles.composer}>
                  <TextArea
                    id="message"
                    labelText="Response"
                    hideLabel
                    rows={3}
                    placeholder={meetingGateMet ? 'Confirm the agreed next step, owner and timing...' : 'Respond to the client in your own words...'}
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
                    {isStreaming ? 'Client is responding...' : meetingGateMet ? 'Confirm next step' : 'Send response'}
                  </Button>
                </div>
              </section>
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
                  canRetryMeeting ? (
                    <Button kind="secondary" disabled={retryMeeting.isPending} onClick={handleRetryMeeting}>
                      {retryMeeting.isPending ? 'Starting live meeting...' : `Retry live meeting (${meetingRetriesRemaining} remaining)`}
                    </Button>
                  ) : (
                    <Button kind="secondary" disabled={retryEngagement.isPending} onClick={handleRetryLead}>
                      {retryEngagement.isPending ? 'Starting lead retry...' : 'Retry this lead from the start'}
                    </Button>
                  )
                )}
              </Stack>
            </Tile>
          )}
        </Column>

        <Column lg={5} md={8} sm={4}>
          <aside className={`${styles.decisionRail} objective-hints`}>
            <section className={`${styles.relationshipPanel} objective-relationship`}>
              <div className={styles.railHeading}>
                <div>
                  <p className={styles.eyebrow}>Relationship state</p>
                  <h2>Meeting gate</h2>
                </div>
                <Tag type={meetingGateMet ? 'green' : 'gray'}>
                  All metrics {meetingThreshold}+
                </Tag>
              </div>
              <Stack gap={5}>
                <RelationshipMeter label="Trust" value={currentState.trust} threshold={meetingThreshold} />
                <RelationshipMeter label="Interest" value={currentState.interest} threshold={meetingThreshold} />
                <RelationshipMeter label="Patience" value={currentState.patience} threshold={meetingThreshold} />
              </Stack>
            </section>

            {(currentBehaviourFeedback || currentState.disclosedFacts.length > 0 || (!isCompleted && (meetingGateMet || clientReadyToClose))) && (
              <MeetingIntelligence
                feedback={currentBehaviourFeedback}
                disclosedFacts={currentState.disclosedFacts}
                readyToClose={!isCompleted && (meetingGateMet || clientReadyToClose)}
              />
            )}
          </aside>
        </Column>
      </Grid>

      <Modal
        open={Boolean(automaticTermination && !terminationDismissed)}
        danger
        modalLabel="Meeting ended"
        modalHeading={automaticTermination?.reason === 'UNPROFESSIONAL_CONDUCT'
          ? 'Meeting failed: unprofessional conduct'
          : 'Meeting failed: relationship threshold breached'}
        primaryButtonText={automaticTermination?.meetingRetryAvailable
          ? (retryMeeting.isPending ? 'Starting live meeting...' : `Retry live meeting (${automaticTermination.meetingRetriesRemaining} remaining)`)
          : (retryEngagement.isPending ? 'Starting lead retry...' : 'Retry this lead from the start')}
        secondaryButtonText="Return to Command Centre"
        primaryButtonDisabled={retryMeeting.isPending || retryEngagement.isPending}
        onRequestSubmit={() => automaticTermination?.meetingRetryAvailable ? handleRetryMeeting() : handleRetryLead()}
        onSecondarySubmit={() => navigate('/dashboard')}
        onRequestClose={() => setTerminationDismissed(true)}
      >
        <Stack gap={5}>
          <p>{automaticTermination?.message}</p>
          <p>{automaticTermination?.meetingRetryAvailable
            ? 'This attempt is preserved for review. Your evidence and preparation remain available; the live conversation restarts with a clean relationship state.'
            : 'This attempt is preserved for review. Retrying creates a new engagement from the same lead with a clean learner state.'}</p>
          {automaticTermination?.retryGuidance.length ? (
            <ul className={styles.terminationGuidance}>
              {automaticTermination.retryGuidance.map((tip) => <li key={tip}>{tip}</li>)}
            </ul>
          ) : null}
          {(retryMeeting.isError || retryEngagement.isError) && (
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="Retry could not be started"
              subtitle="Please try again. Your failed attempt has not been changed."
            />
          )}
        </Stack>
      </Modal>
    </div>
    </ObjectiveTourProvider>
  )
}
