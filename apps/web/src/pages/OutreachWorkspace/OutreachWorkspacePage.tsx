import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Heading,
  InlineNotification,
  Modal,
  Stack,
  Tag,
  TextArea,
  TextInput,
  Tile,
} from '@carbon/react'
import { ArrowRight, CheckmarkFilled, Document, Send, Email, Light, Link as LinkIcon } from '@carbon/icons-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useCapabilityBrief, useOutreach, useSendOutreach, useSubmitCapabilityBrief } from '@/api/hooks/useOutreach'
import { useLeadIntelligence, useResearch } from '@/api/hooks/useLeads'
import { assessDraftSafety, evaluateOutreach, keywordsFrom, stakeholderNameFrom } from '@/lifecycle/coaching/outreachRubric'
import { rankOutreachEvidence } from '@/lifecycle/coaching/outreachEvidence'
import LoadingState from '@/components/shared/LoadingState'
import type { CapabilityBrief, OutreachAttempt, ResearchEvidence } from '@/api/types'
import { getProblemDetail } from '@/api/problemDetails'
import styles from './OutreachWorkspacePage.module.scss'
import { PHASE_LABEL } from '@/lifecycle/phases'

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
        {[...attempts].reverse().map((attempt) => (
          <details key={attempt.id} className={styles.historyItem}>
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
              {attempt.coachingHint && (
                <div className={styles.attemptHint}>
                  <p className={styles.eyebrow}>Coaching note</p>
                  <p>{attempt.coachingHint}</p>
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
  const [historyOpen, setHistoryOpen] = useState(false)
  const { register, handleSubmit, reset, setValue, watch, formState: { errors } } = useForm<EmailFormValues>({
    resolver: zodResolver(emailSchema),
  })

  // Watched so the self-check updates as the learner types.
  const draftBody = watch('body') ?? ''
  const draftSubject = watch('subject') ?? ''
  const draftSafety = assessDraftSafety(draftBody)

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
  const draftReview = evaluateOutreach(draftBody, rubricContext)

  if (isLoading) return <LoadingState />

  const thread = attempts ?? []
  const latestAttempt = thread.at(-1)
  const documentRequired = latestAttempt?.nextAction === 'SUBMIT_CAPABILITY_BRIEF' && brief?.outcome !== 'ACCEPTED'
  const meetingSecured = latestAttempt?.outcome === 'ACCEPTED' || brief?.outcome === 'ACCEPTED'
  const sendEmail = (data: EmailFormValues) => sendOutreach.mutate(data, { onSuccess: () => reset() })
  const evidenceForReference = rankOutreachEvidence(evidence ?? [])
  const leadSignal = evidenceForReference[0] as ResearchEvidence | undefined
  const appendEvidenceReference = (source?: ResearchEvidence) => {
    if (!source) return
    const prefix = draftBody.trim() ? `${draftBody.trim()}\n\n` : ''
    setValue('body', `${prefix}I noticed ${source.note} `, { shouldDirty: true, shouldValidate: true })
  }

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.makeContactHero}>
          <div className={styles.heroIcon}><Email size={28} /></div>
          <div>
            <p className={styles.eyebrow}>Engagement workflow / step 3</p>
            <Heading>{PHASE_LABEL.OUTREACH}</Heading>
            <p className={styles.pageSubtitle}>Send a concise, compelling email to earn a discovery meeting. One clear reason, one low-friction ask.</p>
          </div>
        </div>
      </header>

      <section className={styles.phaseCards} aria-label="Outreach goals">
        <Tile><Light size={22} /><div><strong>What this step is for</strong><span>Earn a meeting by email. One clear reason, one low-friction ask.</span></div></Tile>
        <Tile><CheckmarkFilled size={22} /><div><strong>You are done when</strong><span>The client agrees to meet or requests a specific artifact.</span></div></Tile>
        <Tile><ArrowRight size={22} /><div><strong>What happens next</strong><span>Use the response to prepare a valuable discovery conversation.</span></div></Tile>
      </section>

      <main className={styles.workspace}>
        <section className={styles.primaryColumn}>
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
              <div className={styles.composeWorkspace}>
              <Tile className={styles.emailComposer}>
                <form onSubmit={handleSubmit(sendEmail)}>
                    <div className={styles.composerHeader}>
                      <div><h2>{latestAttempt ? 'Respond to the client' : 'Compose your first outreach email'}</h2><p>Use one evidence-backed reason and make one easy next-step request.</p></div>
                      <Tag type="blue" size="sm">{latestAttempt ? `Attempt ${latestAttempt.attemptNumber + 1}` : 'First contact'}</Tag>
                    </div>
                    <div className={styles.mailMeta}><span>From</span><strong>Consulting Simulation learner</strong></div>
                    <div className={styles.mailMeta}><span>To</span><strong>{rubricContext.personaName ?? 'Client stakeholder'}</strong><small>{rubricContext.companyName ?? 'Client organisation'}</small></div>
                    <TextInput id="subject" labelText="Subject" helperText={`${draftSubject.length} characters`} invalid={Boolean(errors.subject)} invalidText={errors.subject?.message} {...register('subject')} />
                    <div className={styles.checkPills} aria-label="Email requirements">
                      {draftReview.checks.map((check) => (
                        <span key={check.dimension} className={check.met ? styles.met : undefined}>
                          {check.met ? <CheckmarkFilled size={14} /> : <Light size={14} />}{check.label}
                        </span>
                      ))}
                    </div>
                    <TextArea
                      id="body"
                      labelText="Message"
                      rows={6}
                      helperText={`${draftBody.trim() ? draftBody.trim().split(/\s+/).length : 0} words / ${draftBody.length} characters`}
                      invalid={Boolean(errors.body)}
                      invalidText={errors.body?.message}
                      {...register('body')}
                    />
                    {draftSafety.message && <p className={styles.draftNotice} data-risk={draftSafety.risk}>{draftSafety.message}</p>}
                    {sendOutreach.isError && (
                      <InlineNotification kind="error" lowContrast title="Message could not be sent" subtitle={getProblemDetail(sendOutreach.error, 'Please retry after checking the latest client request.')} hideCloseButton />
                    )}
                    <div className={styles.composerFooter}>
                      <small>Evidence and tone are checked when you send. Your message is never sent automatically.</small>
                      <Button type="submit" renderIcon={Send} disabled={sendOutreach.isPending || draftSafety.risk === 'blocking'}>{sendOutreach.isPending ? 'Sending...' : 'Send outreach'}</Button>
                    </div>
                </form>
              </Tile>
              <Tile className={styles.assistPanel}>
                <p className={styles.eyebrow}>Evidence assistant</p>
                <h3>Need help getting started?</h3>
                <p>Build your own message with a verified signal. The assistant never sends or submits work for you.</p>
                {leadSignal && (
                  <section className={styles.bestEvidence} aria-label="Best evidence to use">
                    <p className={styles.eyebrow}>Best evidence to use</p>
                    <strong>{leadSignal.sourceTitle || leadSignal.evidenceType.replace(/_/g, ' ')}</strong>
                    <span>{leadSignal.note}</span>
                    <button type="button" onClick={() => appendEvidenceReference(leadSignal)}>
                      Use this evidence <ArrowRight size={16} />
                    </button>
                  </section>
                )}
                <div className={styles.assistActions}>
                  <button type="button" onClick={() => appendEvidenceReference(leadSignal)} disabled={!leadSignal}>Reference the latest client signal <ArrowRight size={16} /></button>
                  <button type="button" onClick={() => setValue('body', `${draftBody.trim()}${draftBody.trim() ? '\n\n' : ''}Would a 20-minute conversation next week be useful?`, { shouldDirty: true, shouldValidate: true })}>Invite a short conversation <ArrowRight size={16} /></button>
                  <button type="button" onClick={() => setValue('subject', `Idea for ${rubricContext.companyName ?? 'your team'}`, { shouldDirty: true, shouldValidate: true })}>Start a clear subject line <ArrowRight size={16} /></button>
                </div>
                {latestAttempt?.coachingHint && <div className={styles.coachingCallout}><strong>Latest coaching</strong><span>{latestAttempt.coachingHint}</span></div>}
              </Tile>
              </div>
            )}

            {brief && brief.outcome !== 'FOLLOW_UP_REQUIRED' && !documentRequired && !meetingSecured && <BriefReview brief={brief} />}
            <section className={styles.evidenceStrip} aria-label="Evidence you can reference">
              <div className={styles.stripHeading}><div><p className={styles.eyebrow}>Grounded context</p><h2>Evidence you can reference</h2></div><span>{evidenceForReference.length} available</span></div>
              {evidenceForReference.length > 0 ? (
                <div className={styles.evidenceCards}>
                  {evidenceForReference.slice(0, 4).map((item) => (
                    <button type="button" key={item.id} onClick={() => appendEvidenceReference(item)}>
                      <LinkIcon size={18} /><strong>{item.sourceTitle || item.evidenceType.replace(/_/g, ' ')}</strong><p>{item.note}</p><small>Add to email <ArrowRight size={14} /></small>
                    </button>
                  ))}
                </div>
              ) : <p className={styles.emptyReply}>Return to Research the client to gather evidence you can reference here.</p>}
            </section>
        </section>

        <aside className={styles.decisionRail}>
            {latestAttempt?.clientReply ? (
              <section className={styles.clientResponse} aria-label="Latest client response" aria-live="polite">
                <div className={styles.responseClientIdentity}>
                  <div className={styles.clientMonogram}>{(intelligence?.companyName ?? 'C').slice(0, 1)}</div>
                  <div>
                    <strong>{intelligence?.companyName ?? 'Client organisation'}</strong>
                    <span>{rubricContext.personaName ?? 'Client stakeholder'} <Tag type="blue" size="sm">{intelligence?.industry ?? 'Client'}</Tag></span>
                  </div>
                </div>
                <div className={styles.clientResponseHeading}>
                  <div>
                    <p className={styles.eyebrow}>Latest client response</p>
                    <h2>What the client said</h2>
                  </div>
                  <div className={styles.responseActions}>
                    <Tag type={OUTCOME_TAG[latestAttempt.outcome]} size="sm">{latestAttempt.outcome.replace(/_/g, ' ')}</Tag>
                    {thread.length > 1 && <Button kind="ghost" size="sm" onClick={() => setHistoryOpen(true)}>History</Button>}
                  </div>
                </div>
                <blockquote>{latestAttempt.clientReply}</blockquote>
                {(latestAttempt.requestTitle || latestAttempt.coachingHint) && (
                  <div className={styles.responseGuidance}>
                    <strong>{latestAttempt.requestTitle ?? 'Recommended next step'}</strong>
                    <span>{latestAttempt.requestSummary ?? latestAttempt.coachingHint}</span>
                  </div>
                )}
              </section>
            ) : (
              <Tile className={`${styles.clientOverview} ${styles.latestReply}`}>
                <div className={styles.replyHeading}>
                  <div><p className={styles.eyebrow}>Client signal</p><h2>What to use</h2></div>
                </div>
                {leadSignal ? <p className={styles.clientReply}>{leadSignal.note}</p> : <p className={styles.emptyReply}>Research a client signal before making contact.</p>}
              </Tile>
            )}

            <Tile className={styles.checklistPanel}>
              <div className={styles.overviewHeading}><h3>Outreach checklist</h3><strong>{draftReview.metCount}/4</strong></div>
              {draftReview.checks.map((check) => <div key={check.dimension} className={styles.checklistRow}>{check.met ? <CheckmarkFilled size={16} /> : <Light size={16} />}<span>{check.label}</span></div>)}
            </Tile>

            {!latestAttempt?.clientReply && <Tile className={styles.nextActionPanel}>
              <Light size={22} /><div><p className={styles.eyebrow}>Next best action</p><h3>{latestAttempt?.coachingHint ? 'Refine before you send' : 'Use one client signal'}</h3><p>{latestAttempt?.coachingHint ?? 'Reference a verified source, then ask for a short, time-bound conversation.'}</p></div>
            </Tile>}

            {!latestAttempt?.clientReply && latestAttempt?.requestRequirements?.length && (
              <Tile className={styles.hintPanel}>
                <p className={styles.eyebrow}>What the client is asking for</p>
                <h3>{latestAttempt.requestTitle}</h3>
                <p>{latestAttempt.requestSummary}</p>
                <ul>
                  {latestAttempt.requestRequirements.map((requirement) => <li key={requirement}>{requirement}</li>)}
                </ul>
              </Tile>
            )}

            {brief?.outcome === 'FOLLOW_UP_REQUIRED' && <BriefReview brief={brief} />}
        </aside>
      </main>
      <Modal open={historyOpen} modalHeading="Outreach conversation" passiveModal onRequestClose={() => setHistoryOpen(false)}>
        <ThreadHistory attempts={thread} />
      </Modal>
    </div>
  )
}
