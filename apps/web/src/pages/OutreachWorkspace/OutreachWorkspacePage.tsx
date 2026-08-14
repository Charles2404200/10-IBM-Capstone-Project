import { useEffect, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Stack,
  Tag,
  TextArea,
  TextInput,
  Tile,
} from '@carbon/react'
import { ArrowRight, CheckmarkFilled, Document, Send } from '@carbon/icons-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useCapabilityBrief, useOutreach, useSendOutreach, useSubmitCapabilityBrief } from '@/api/hooks/useOutreach'
import { useLeadIntelligence, useResearch } from '@/api/hooks/useLeads'
import OutreachSelfCheck from '@/lifecycle/components/OutreachSelfCheck'
import { keywordsFrom, stakeholderNameFrom } from '@/lifecycle/coaching/outreachRubric'
import LoadingState from '@/components/shared/LoadingState'
import type { CapabilityBrief, OutreachAttempt } from '@/api/types'
import { getProblemDetail } from '@/api/problemDetails'
import styles from './OutreachWorkspacePage.module.scss'
import { PHASE_LABEL } from '@/lifecycle/phases'
import PageHeader from '@/lifecycle/components/PageHeader'
import shell from '@/lifecycle/lifecycle.module.scss'
import { useExperience } from '@/lifecycle/experience'

const emailSchema = z.object({
  subject: z.string().min(5, 'Enter a clear subject').max(200),
  body: z.string().min(50, 'Message must be at least 50 characters').max(5000),
})

const briefSchema = z.object({
  relevantExperience: z.string().trim().min(80, 'Add at least 80 characters of relevant experience').max(3000),
  approach: z.string().trim().min(80, 'Explain the approach in at least 80 characters').max(3000),
  caseExample: z.string().trim().min(80, 'Add a concrete case example').max(3000),
  clientFit: z.string().trim().min(80, 'Explain why this fits the client').max(3000),
})

type EmailFormValues = z.infer<typeof emailSchema>
type BriefFormValues = z.infer<typeof briefSchema>

const OUTCOME_TAG: Record<OutreachAttempt['outcome'], 'green' | 'magenta' | 'red' | 'gray'> = {
  ACCEPTED: 'green',
  FOLLOW_UP_REQUIRED: 'magenta',
  REJECTED: 'red',
  PENDING: 'gray',
}

function ScoreBar({ label, value }: { label: string; value: number | null }) {
  if (value === null) return null
  const tone = value >= 70 ? styles.scoreGood : value >= 40 ? styles.scoreModerate : styles.scoreLow
  return (
    <div className={styles.scoreRow}>
      <span>{label}</span>
      <div className={styles.scoreTrack}><div className={tone} style={{ width: `${value}%` }} /></div>
      <strong>{value}</strong>
    </div>
  )
}

function ThreadHistory({ attempts }: { attempts: OutreachAttempt[] }) {
  if (attempts.length === 0) return null
  return (
    <section className={styles.history} aria-label="Outreach history">
      <div className={styles.historyHeading}>
        <h3>Conversation history</h3>
        <span>{attempts.length} {attempts.length === 1 ? 'message' : 'messages'}</span>
      </div>
      <Stack gap={3}>
        {[...attempts].reverse().map((attempt, index) => (
          <details key={attempt.id} className={styles.historyItem} open={index === 0}>
            <summary>
              <span>Attempt #{attempt.attemptNumber}</span>
              <Tag type={OUTCOME_TAG[attempt.outcome]} size="sm">{attempt.outcome.replace(/_/g, ' ')}</Tag>
              <span className={styles.historySubject}>{attempt.subject}</span>
            </summary>
            <div className={styles.historyBody}>
              <div>
                <p className={styles.eyebrow}>Your message</p>
                <p>{attempt.body}</p>
              </div>
              {attempt.clientReply && (
                <div className={styles.clientReplyCompact}>
                  <p className={styles.eyebrow}>Client response</p>
                  <p>{attempt.clientReply}</p>
                </div>
              )}
              <div className={styles.scoreList}>
                <ScoreBar label="Personalisation" value={attempt.scorePersonalisation} />
                <ScoreBar label="Relevance" value={attempt.scoreRelevance} />
                <ScoreBar label="Clarity" value={attempt.scoreClarity} />
                <ScoreBar label="Call to action" value={attempt.scoreCallToAction} />
              </div>
            </div>
          </details>
        ))}
      </Stack>
    </section>
  )
}

function BriefReview({ brief }: { brief: CapabilityBrief }) {
  const outcomeTag = OUTCOME_TAG[brief.outcome]
  return (
    <Tile className={styles.briefReview}>
      <Stack gap={4}>
        <div className={styles.reviewHeading}>
          <div>
            <p className={styles.eyebrow}>Client review</p>
            <h2>Capability brief submitted</h2>
          </div>
          <Tag type={outcomeTag} size="md">{brief.outcome.replace(/_/g, ' ')}</Tag>
        </div>
        {brief.clientReply && <p className={styles.clientReply}>{brief.clientReply}</p>}
        <div className={styles.reviewGrid}>
          <ScoreBar label="Client fit" value={brief.scoreClientFit} />
          <ScoreBar label="Industry relevance" value={brief.scoreIndustryRelevance} />
          <ScoreBar label="Evidence quality" value={brief.scoreEvidenceQuality} />
          <ScoreBar label="Clarity" value={brief.scoreClarity} />
          <ScoreBar label="Credibility" value={brief.scoreCredibility} />
        </div>
      </Stack>
    </Tile>
  )
}

function CapabilityBriefEditor({
  engagementId,
  brief,
  requirements,
}: {
  engagementId: string
  brief: CapabilityBrief | null | undefined
  requirements: string[]
}) {
  const submitBrief = useSubmitCapabilityBrief(engagementId)
  const { register, handleSubmit, reset, formState: { errors } } = useForm<BriefFormValues>({
    resolver: zodResolver(briefSchema),
    defaultValues: brief ?? undefined,
  })

  useEffect(() => {
    if (brief) {
      reset({
        relevantExperience: brief.relevantExperience,
        approach: brief.approach,
        caseExample: brief.caseExample,
        clientFit: brief.clientFit,
      })
    }
  }, [brief, reset])

  return (
    <Tile className={styles.editor}>
      <form onSubmit={handleSubmit((data) => submitBrief.mutate(data))}>
        <Stack gap={5}>
          <div className={styles.editorHeading}>
            <div>
              <p className={styles.eyebrow}>Requested deliverable</p>
              <h2>Capability brief</h2>
              <p>Write the concise document the client requested. The review evaluates this artifact, not another email.</p>
            </div>
            <Document size={28} />
          </div>
          {requirements.length > 0 && (
            <div className={styles.requirementList}>
              <p>Include</p>
              {requirements.map((requirement) => (
                <span key={requirement}><CheckmarkFilled size={16} />{requirement}</span>
              ))}
            </div>
          )}
          <div className={styles.editorGrid}>
            <TextArea
              id="relevantExperience"
              labelText="Relevant experience"
              rows={4}
              invalid={Boolean(errors.relevantExperience)}
              invalidText={errors.relevantExperience?.message}
              {...register('relevantExperience')}
            />
            <TextArea
              id="approach"
              labelText="Implementation approach"
              rows={4}
              invalid={Boolean(errors.approach)}
              invalidText={errors.approach?.message}
              {...register('approach')}
            />
            <TextArea
              id="caseExample"
              labelText="Relevant case example"
              rows={4}
              invalid={Boolean(errors.caseExample)}
              invalidText={errors.caseExample?.message}
              {...register('caseExample')}
            />
            <TextArea
              id="clientFit"
              labelText="Why this fits the client"
              rows={4}
              invalid={Boolean(errors.clientFit)}
              invalidText={errors.clientFit?.message}
              {...register('clientFit')}
            />
          </div>
          {submitBrief.isError && (
            <InlineNotification
              kind="error"
              lowContrast
              title="Brief could not be submitted"
              subtitle={getProblemDetail(submitBrief.error, 'The client request may have changed. Refresh the workspace and try again.')}
              hideCloseButton
            />
          )}
          <Button type="submit" renderIcon={Send} disabled={submitBrief.isPending}>
            {submitBrief.isPending ? 'Submitting to client...' : 'Submit to Client'}
          </Button>
        </Stack>
      </form>
    </Tile>
  )
}

export default function OutreachWorkspacePage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()
  const { data: attempts, isLoading } = useOutreach(engagementId!)
  const { data: brief } = useCapabilityBrief(engagementId!)
  const sendOutreach = useSendOutreach(engagementId!)
  const { data: intelligence } = useLeadIntelligence(engagementId!)
  const { data: evidence } = useResearch(engagementId!)
  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<EmailFormValues>({
    resolver: zodResolver(emailSchema),
  })

  // Watched so the self-check updates as the learner types.
  const draftBody = watch('body') ?? ''

  // Context for the self-check, derived from data this page already has, so it
  // costs no extra round trip.
  const rubricContext = useMemo(
    () => ({
      personaName: stakeholderNameFrom(intelligence?.decisionMaker?.value),
      companyName: intelligence?.companyName ?? null,
      keywords: keywordsFrom([
        ...(evidence ?? []).map((item) => item.note),
        intelligence?.painSeverity?.value,
        intelligence?.technologyStack?.value,
      ]),
    }),
    [evidence, intelligence]
  )

  if (isLoading) return <LoadingState />

  const thread = attempts ?? []
  const latestAttempt = thread.at(-1)
  const documentRequired = latestAttempt?.nextAction === 'SUBMIT_CAPABILITY_BRIEF' && brief?.outcome !== 'ACCEPTED'
  const meetingSecured = latestAttempt?.outcome === 'ACCEPTED' || brief?.outcome === 'ACCEPTED'
  const { isFirstEngagement } = useExperience()

  const sendEmail = (data: EmailFormValues) => sendOutreach.mutate(data, { onSuccess: () => reset() })

  return (
    <div className={`${styles.page} ${shell.fixedShellFrame}`}>
      {/* The brief explains how to do the step; once it is done it is just
          height where the outcome belongs. */}
      {/* Suppressed once the meeting is won, because instructions for a finished
          task are just height where the outcome belongs. Suppressed on a repeat
          attempt too -- but not during someone's first engagement, where a
          second attempt is exactly when they most need the step restated. */}
      <PageHeader
        phase="OUTREACH"
        brief={!meetingSecured && (isFirstEngagement || !latestAttempt)}
      />

      <Grid fullWidth className={`${styles.workspaceGrid} ${shell.fixedShellBody}`}>
        <Column lg={10} md={8} sm={4} className={shell.fixedShellFrame}>
          <Stack gap={5} className={`${styles.composeColumn} ${shell.scrollPanel}`}>
            {meetingSecured && (
              <Tile className={styles.successPanel}>
                <div>
                  <p className={styles.eyebrow}>Next phase unlocked</p>
                  <h2>Meeting secured</h2>
                  <p>The client has accepted a discovery conversation. Carry this context into your preparation.</p>
                </div>
                <Button renderIcon={ArrowRight} onClick={() => navigate(`/dashboard/engagements/${engagementId}/preparation`)}>
                  Continue to {PHASE_LABEL.MEETING_PREPARATION}
                </Button>
              </Tile>
            )}

            {documentRequired && brief?.outcome !== 'FOLLOW_UP_REQUIRED' && (
              <CapabilityBriefEditor
                engagementId={engagementId!}
                brief={brief}
                requirements={latestAttempt?.requestRequirements ?? []}
              />
            )}

            {documentRequired && brief?.outcome === 'FOLLOW_UP_REQUIRED' && (
              <>
                <BriefReview brief={brief} />
                <CapabilityBriefEditor
                  engagementId={engagementId!}
                  brief={brief}
                  requirements={latestAttempt?.requestRequirements ?? []}
                />
              </>
            )}

            {!meetingSecured && !documentRequired && (
              <Tile className={styles.editor}>
                <form onSubmit={handleSubmit(sendEmail)}>
                  <Stack gap={4}>
                    <h2 className={styles.formTitle}>
                      {latestAttempt ? 'Respond to the client' : 'Compose outreach'}
                    </h2>
                    <TextInput id="subject" labelText="Subject" invalid={Boolean(errors.subject)} invalidText={errors.subject?.message} {...register('subject')} />
                    <TextArea
                      id="body"
                      className={styles.messageField}
                      labelText="Message"
                      rows={10}
                      helperText={`Minimum 50 characters — ${draftBody.length} so far.`}
                      invalid={Boolean(errors.body)}
                      invalidText={errors.body?.message}
                      {...register('body')}
                    />
                    {sendOutreach.isError && (
                      <InlineNotification kind="error" lowContrast title="Message could not be sent" subtitle={getProblemDetail(sendOutreach.error, 'Please retry after checking the latest client request.')} hideCloseButton />
                    )}
                    <Button type="submit" renderIcon={Send} disabled={sendOutreach.isPending}>
                      {sendOutreach.isPending ? 'Sending...' : 'Send message'}
                    </Button>
                  </Stack>
                </form>
              </Tile>
            )}

            {brief && brief.outcome !== 'FOLLOW_UP_REQUIRED' && !documentRequired && !meetingSecured && <BriefReview brief={brief} />}
          </Stack>
        </Column>

        <Column lg={6} md={8} sm={4} className={shell.fixedShellFrame}>
          <aside className={`${styles.decisionRail} ${shell.scrollPanel}`}>
            <Tile className={styles.latestReply}>
              <Stack gap={4}>
                <div className={styles.replyHeading}>
                  <div>
                    <p className={styles.eyebrow}>Latest client response</p>
                    <h2>{documentRequired ? 'Client requested a document' : meetingSecured ? 'Client accepted the meeting' : 'Client response'}</h2>
                  </div>
                  {meetingSecured ? (
                    <Tag type="green" size="sm">ACCEPTED</Tag>
                  ) : (
                    latestAttempt && (
                      <Tag type={OUTCOME_TAG[latestAttempt.outcome]} size="sm">
                        {latestAttempt.outcome.replace(/_/g, ' ')}
                      </Tag>
                    )
                  )}
                </div>
                {meetingSecured ? (
                  <p className={styles.clientReply}>
                    {brief?.clientReply ??
                      latestAttempt?.clientReply ??
                      'The client has agreed to meet.'}
                  </p>
                ) : latestAttempt?.clientReply ? (
                  <p className={styles.clientReply}>{latestAttempt.clientReply}</p>
                ) : (
                  <p className={styles.emptyReply}>Send your first message to receive a client response.</p>
                )}
              </Stack>
            </Tile>

            {latestAttempt?.requestRequirements?.length ? (
              <Tile className={styles.hintPanel}>
                <p className={styles.eyebrow}>What the client is asking for</p>
                <h3>{latestAttempt.requestTitle}</h3>
                <p>{latestAttempt.requestSummary}</p>
                <ul>
                  {latestAttempt.requestRequirements.map((requirement) => <li key={requirement}>{requirement}</li>)}
                </ul>
              </Tile>
            ) : latestAttempt?.clientReply && !meetingSecured ? (
              <Tile className={styles.hintPanel}>
                <p className={styles.eyebrow}>Response-based hint</p>
                <h3>Address the latest response</h3>
                <p>Use the client’s wording above to acknowledge their constraint, then make one specific next-step request.</p>
              </Tile>
            ) : null}

            {!meetingSecured && !documentRequired && (
              <OutreachSelfCheck body={draftBody} context={rubricContext} explain={!latestAttempt} />
            )}

            {brief?.outcome === 'FOLLOW_UP_REQUIRED' && <BriefReview brief={brief} />}

            {/* The thread sits with the client's latest reply because that is
                what it is: what has been said so far. Keeping it in the compose
                column pushed the Send button 900px below the fold. */}
            <ThreadHistory attempts={thread} />
          </aside>
        </Column>
      </Grid>
    </div>
  )
}
