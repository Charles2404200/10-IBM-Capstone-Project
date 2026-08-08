import { useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Button,
  Tag,
  Tile,
  TextInput,
  TextArea,
  Select,
  SelectItem,
  Checkbox,
  DatePicker,
  DatePickerInput,
  ProgressIndicator,
  ProgressStep,
  Accordion,
  AccordionItem,
  Tooltip,
} from '@carbon/react'
import {
  Add, ArrowRight, Locked, Link as LinkIcon, Search,
  ChartLine, Devices, UserMultiple, Document, Information,
  CheckmarkFilled, CircleDash,
} from '@carbon/icons-react'
import { useForm, Controller } from 'react-hook-form'
import { useEngagement } from '@/api/hooks/useEngagements'
import { useLeadIntelligence, useResearch, useResearchGateStatus, useCompleteResearch, useSaveResearch } from '@/api/hooks/useLeads'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConfidenceLevel, EngagementPhase, EvidenceType, IntelligenceField, ResearchEvidence } from '@/api/types'
import styles from './ClientIntelligencePage.module.scss'

const EVIDENCE_TYPES: Exclude<EvidenceType, 'HYPOTHESIS'>[] = [
  'COMPANY_NEWS', 'FINANCIAL_SIGNAL', 'TECHNOLOGY_INDICATOR',
  'STAKEHOLDER_PROFILE', 'MARKET_TREND', 'OTHER',
]

const CONFIDENCE_LEVELS: ConfidenceLevel[] = ['LOW', 'MEDIUM', 'HIGH']

const CONFIDENCE_TAG_TYPE: Record<ConfidenceLevel, 'red' | 'warm-gray' | 'green'> = {
  LOW: 'red',
  MEDIUM: 'warm-gray',
  HIGH: 'green',
}

/** Renders "E-01", "E-02", ... from the backend's stable per-engagement sequence. */
function evidenceCode(sequenceNo: number): string {
  return `E-${String(sequenceNo).padStart(2, '0')}`
}

interface FormValues {
  note: string
  evidenceType: EvidenceType
  sourceUrl: string
  sourceTitle: string
  occurredOn: string
  confidence: ConfidenceLevel
}

interface HypothesisFormValues {
  hypothesis: string
  confidence: ConfidenceLevel
  supportingEvidenceIds: string[]
}

// ─── Research Actions: guided prompts that steer the learner toward the right
// evidence category, instead of a blank "note" field (still requires the
// learner to enter their own real finding — no fabricated data is injected). ───
const RESEARCH_ACTIONS: { type: Exclude<EvidenceType, 'HYPOTHESIS'>; label: string; prompt: string; icon: typeof Search }[] = [
  { type: 'COMPANY_NEWS', label: 'Company News', prompt: 'What recent news or announcements have you found about this client?', icon: Document },
  { type: 'STAKEHOLDER_PROFILE', label: 'Stakeholder Research', prompt: 'What have you learned about the key decision makers and their priorities?', icon: UserMultiple },
  { type: 'FINANCIAL_SIGNAL', label: 'Financial Signals', prompt: 'What financial signals (budget, funding, spend) have you uncovered?', icon: ChartLine },
  { type: 'TECHNOLOGY_INDICATOR', label: 'Technology Research', prompt: 'What does their current technology stack or infrastructure look like?', icon: Devices },
]

function EvidenceCard({ item, codeById }: { item: ResearchEvidence; codeById: Map<string, string> }) {
  return (
    <Tile className={styles.evidenceCard}>
      <Stack gap={3}>
        <div className={styles.evidenceCardHeader}>
          <span className={styles.evidenceCode}>{evidenceCode(item.sequenceNo)}</span>
          <Tag type="blue" size="sm">{item.evidenceType.replace(/_/g, ' ')}</Tag>
          <Tag type={CONFIDENCE_TAG_TYPE[item.confidence]} size="sm">
            {item.confidence} confidence
          </Tag>
        </div>

        <p className={styles.evidenceNote}>{item.note}</p>

        {item.supportingEvidenceIds.length > 0 && (
          <div className={styles.supportingEvidence}>
            <LinkIcon size={14} />
            <span>
              Supported by{' '}
              {item.supportingEvidenceIds
                .map((id) => codeById.get(id) ?? '?')
                .join(', ')}
            </span>
          </div>
        )}

        {(item.sourceTitle || item.sourceUrl || item.occurredOn) && (
          <div className={styles.sourceMeta}>
            {item.sourceTitle && (
              <div className={styles.sourceMetaRow}>
                <span className={styles.sourceMetaLabel}>Source</span>
                <span className={styles.sourceTitle}>{item.sourceTitle}</span>
              </div>
            )}
            {item.occurredOn && (
              <div className={styles.sourceMetaRow}>
                <span className={styles.sourceMetaLabel}>Observed</span>
                <span className={styles.sourceDate}>{item.occurredOn}</span>
              </div>
            )}
            {item.sourceUrl && (
              <a href={item.sourceUrl} target="_blank" rel="noreferrer" className={styles.sourceLinkButton}>
                View source
              </a>
            )}
          </div>
        )}
      </Stack>
    </Tile>
  )
}

// ─── Consulting lifecycle stepper, driven by the real backend phase (not a
// hardcoded local guess), so completed / current / upcoming always reflect
// actual engagement state. ───
const PHASE_ORDER: EngagementPhase[] = [
  'LEAD', 'CLIENT_INTELLIGENCE', 'OUTREACH', 'MEETING_PREPARATION',
  'LIVE_MEETING', 'PROPOSAL', 'OUTCOME', 'REVIEW', 'COMPLETED',
]

const PHASE_LABELS: Record<EngagementPhase, string> = {
  LEAD: 'Lead',
  CLIENT_INTELLIGENCE: 'Client Intelligence',
  OUTREACH: 'Outreach',
  MEETING_PREPARATION: 'Meeting Prep',
  LIVE_MEETING: 'Live Meeting',
  PROPOSAL: 'Proposal',
  OUTCOME: 'Outcome',
  REVIEW: 'AI Review',
  COMPLETED: 'Completed',
}

function PhaseStepper({ currentPhase }: { currentPhase: EngagementPhase }) {
  const currentIndex = PHASE_ORDER.indexOf(currentPhase)
  return (
    <ProgressIndicator spaceEqually>
      {PHASE_ORDER.map((phase, idx) => (
        <ProgressStep
          key={phase}
          label={PHASE_LABELS[phase]}
          current={idx === currentIndex}
          complete={idx < currentIndex}
          disabled={idx > currentIndex}
        />
      ))}
    </ProgressIndicator>
  )
}

function IntelField({ label, field }: { label: string; field: IntelligenceField }) {
  const supportingEvidence = field?.supportingEvidence ?? []
  return (
    <div className={styles.intelField}>
      <span className={styles.intelFieldLabel}>{label}</span>
      {field?.value ? (
        <>
          <span className={styles.intelFieldValue}>{field.value}</span>
          {supportingEvidence.length > 0 && (
            <span className={styles.intelFieldSource}>
              Based on {supportingEvidence.map(evidenceCode).join(', ')}
            </span>
          )}
        </>
      ) : (
        <span className={styles.intelFieldUnknown}>Unknown — keep researching</span>
      )}
    </div>
  )
}

/** Requirement row for {@link ResearchGateChecklist} — met (✓ blue) or unmet (○ gray). */
function GateRequirement({ met, label }: { met: boolean; label: string }) {
  return (
    <div className={styles.gateRequirement}>
      {met ? <CheckmarkFilled size={16} className={styles.gateRequirementMetIcon} /> : <CircleDash size={16} className={styles.gateRequirementUnmetIcon} />}
      <span className={met ? styles.gateRequirementMetLabel : styles.gateRequirementUnmetLabel}>{label}</span>
    </div>
  )
}

/** Enforces the "no spamming Next" business rule: Outreach only unlocks once
 *  the learner has satisfied real research conditions server-side
 *  (see backend `ResearchReadinessPolicy`), not merely "some evidence exists". */
function ResearchGateChecklist({
  engagementId,
  onProceed,
}: {
  engagementId: string
  onProceed: () => void
}) {
  const { data: gate } = useResearchGateStatus(engagementId)
  const completeResearch = useCompleteResearch(engagementId)

  if (!gate) return null

  const handleProceed = () => {
    if (gate.researchCompleted) {
      onProceed()
      return
    }
    completeResearch.mutate(undefined, { onSuccess: () => onProceed() })
  }

  return (
    <div className={styles.researchGate}>
      <h4 className={styles.researchGateTitle}>Ready for Outreach?</h4>
      <Stack gap={2}>
        <GateRequirement
          met={gate.evidenceCount >= gate.requiredEvidenceCount}
          label={`At least ${gate.requiredEvidenceCount} evidence items (${gate.evidenceCount}/${gate.requiredEvidenceCount})`}
        />
        <GateRequirement met={gate.hasStakeholderEvidence} label="Stakeholder evidence identified" />
        <GateRequirement met={gate.hasHypothesis} label="Hypothesis submitted" />
        <GateRequirement
          met={gate.confidencePercent >= gate.requiredConfidencePercent}
          label={`Research confidence at least ${gate.requiredConfidencePercent}% (${gate.confidencePercent}%)`}
        />
      </Stack>

      {completeResearch.isError && (
        <p className={styles.gateError}>Complete the requirements above before proceeding.</p>
      )}

      <Button
        renderIcon={gate.ready ? ArrowRight : Locked}
        kind="secondary"
        disabled={!gate.ready || completeResearch.isPending}
        onClick={handleProceed}
        style={{ marginTop: '1rem' }}
      >
        {completeResearch.isPending ? 'Advancing…' : 'Proceed to Outreach'}
      </Button>
    </div>
  )
}

/** The "Client Profile" workspace panel — hidden lead intelligence revealed
 *  progressively as research evidence accumulates. */
function ClientProfilePanel({ engagementId }: { engagementId: string }) {
  const { data: intel, isLoading } = useLeadIntelligence(engagementId)

  if (isLoading || !intel) {
    return (
      <div className={styles.clientProfile}>
        <div className={styles.clientProfileHeader}>
          <h3>Client Profile</h3>
        </div>
        <p className={styles.intelFieldUnknown}>Select a lead to view client intelligence.</p>
      </div>
    )
  }

  return (
    <div className={styles.clientProfile}>
      <div className={styles.clientProfileHeader}>
        <h3>{intel.companyName}</h3>
        <Tag type="gray" size="sm">{intel.industry}</Tag>
      </div>

      <div className={styles.confidenceMeter}>
        <div className={styles.confidenceLabelRow}>
          <span className={styles.confidenceLabelWithHelp}>
            Research confidence
            <Tooltip
              align="top"
              label={
                intel.confidenceFactors?.length
                  ? `Why ${intel.confidenceLabel}? ${intel.confidenceFactors.join(' · ')}`
                  : 'Confidence combines how many research areas you have covered with the average reliability of your findings — not just how many notes you have written.'
              }
            >
              <button type="button" aria-label="How is confidence calculated?" style={{ background: 'none', border: 'none', padding: 0, display: 'flex' }}>
                <Information size={14} />
              </button>
            </Tooltip>
          </span>
          <span>{intel.confidenceLabel} · {intel.evidenceCount} evidence</span>
        </div>
        <div className={styles.confidenceTrack}>
          <div
            className={styles.confidenceFill}
            style={{ width: `${intel.confidenceScore}%` }}
          />
        </div>
      </div>

      <IntelField label="Decision maker" field={intel.decisionMaker} />
      <IntelField label="Pain severity" field={intel.painSeverity} />
      <IntelField label="Technology stack" field={intel.technologyStack} />
      <IntelField label="Budget signal" field={intel.budgetSignal} />
      <IntelField label="Potential value" field={intel.potentialValueRange} />
    </div>
  )
}

/** Consulting process: Research → Evidence → Pattern → Hypothesis → Validation.
 *  Kept deliberately separate from the evidence form — a hypothesis is a
 *  synthesis step, not another evidence item. */
function HypothesisWorkspace({
  evidence,
  codeById,
  engagementId,
}: {
  evidence: ResearchEvidence[]
  codeById: Map<string, string>
  engagementId: string
}) {
  const saveResearch = useSaveResearch(engagementId)
  const [composing, setComposing] = useState(false)
  const hypotheses = useMemo(
    () => (evidence ?? []).filter((e) => e.evidenceType === 'HYPOTHESIS').sort((a, b) => b.sequenceNo - a.sequenceNo),
    [evidence]
  )
  const citableEvidence = useMemo(() => (evidence ?? []).filter((e) => e.evidenceType !== 'HYPOTHESIS'), [evidence])

  const { register, handleSubmit, reset, control, formState: { errors } } = useForm<HypothesisFormValues>({
    defaultValues: { confidence: 'MEDIUM', supportingEvidenceIds: [] },
  })

  const onSubmit = (data: HypothesisFormValues) => {
    saveResearch.mutate(
      {
        note: data.hypothesis,
        hypothesis: data.hypothesis,
        evidenceType: 'HYPOTHESIS',
        confidence: data.confidence,
        supportingEvidenceIds: data.supportingEvidenceIds?.length ? data.supportingEvidenceIds : undefined,
      },
      {
        onSuccess: () => {
          reset({ confidence: 'MEDIUM', supportingEvidenceIds: [] })
          setComposing(false)
        },
      }
    )
  }

  return (
    <div className={styles.hypothesisWorkspace}>
      <div className={styles.hypothesisWorkspaceHeader}>
        <h3>Hypothesis</h3>
        <Button kind="ghost" size="sm" onClick={() => setComposing((c) => !c)}>
          {hypotheses.length > 0 ? 'Refine hypothesis' : 'Add hypothesis'}
        </Button>
      </div>

      {hypotheses.length === 0 && !composing && (
        <p className={styles.hypothesisEmpty}>
          No hypothesis yet. Once you've gathered a few pieces of evidence, form a hypothesis
          about the client's underlying problem.
        </p>
      )}

      {hypotheses.map((h) => (
        <div key={h.id} className={styles.hypothesisCard}>
          <p className={styles.hypothesisStatement}>&ldquo;{h.hypothesis ?? h.note}&rdquo;</p>
          <div className={styles.hypothesisMetaRow}>
            <span>
              <span className={styles.hypothesisSupportLabel}>Supporting evidence</span>
              <span className={styles.hypothesisSupportCodes}>
                {h.supportingEvidenceIds.length > 0
                  ? h.supportingEvidenceIds.map((id) => codeById.get(id) ?? '?').join(' · ')
                  : 'None cited'}
              </span>
            </span>
            <Tag type={CONFIDENCE_TAG_TYPE[h.confidence]} size="sm">{h.confidence} confidence</Tag>
          </div>
        </div>
      ))}

      {composing && (
        <form onSubmit={handleSubmit(onSubmit)} style={{ marginTop: '1rem' }}>
          <Stack gap={4}>
            <TextArea
              id="hypothesis-statement"
              labelText="Hypothesis statement"
              placeholder="e.g. NorthPeak's inventory issues stem from fragmented warehouse systems"
              rows={2}
              invalid={Boolean(errors.hypothesis)}
              invalidText="Required"
              {...register('hypothesis', { required: true })}
            />

            {citableEvidence.length > 0 && (
              <div>
                <p className={styles.linkLabel}>Supporting evidence</p>
                <Stack gap={2}>
                  {citableEvidence.map((e) => (
                    <Controller
                      key={e.id}
                      control={control}
                      name="supportingEvidenceIds"
                      render={({ field }) => (
                        <Checkbox
                          id={`support-${e.id}`}
                          labelText={`${evidenceCode(e.sequenceNo)} — ${e.note.slice(0, 60)}`}
                          checked={field.value?.includes(e.id) ?? false}
                          onChange={(_, { checked }) => {
                            const current = field.value ?? []
                            field.onChange(checked ? [...current, e.id] : current.filter((id) => id !== e.id))
                          }}
                        />
                      )}
                    />
                  ))}
                </Stack>
              </div>
            )}

            <Select id="hypothesis-confidence" labelText="How confident are you?" {...register('confidence')}>
              {CONFIDENCE_LEVELS.map((c) => (
                <SelectItem key={c} value={c} text={c} />
              ))}
            </Select>

            <Stack gap={3} orientation="horizontal">
              <Button type="submit" size="sm" disabled={saveResearch.isPending}>Save hypothesis</Button>
              <Button kind="ghost" size="sm" onClick={() => setComposing(false)}>Cancel</Button>
            </Stack>
          </Stack>
        </form>
      )}
    </div>
  )
}

export default function ClientIntelligencePage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()
  const { data: engagement } = useEngagement(engagementId!)
  const { data: evidence, isLoading, isError } = useResearch(engagementId!)
  const saveResearch = useSaveResearch(engagementId!)
  const noteRef = useRef<HTMLTextAreaElement | null>(null)
  const [activeAction, setActiveAction] = useState<Exclude<EvidenceType, 'HYPOTHESIS'> | null>(null)

  const { register, handleSubmit, reset, setValue, control, formState: { errors } } = useForm<FormValues>({
    defaultValues: {
      evidenceType: 'COMPANY_NEWS',
      confidence: 'MEDIUM',
    },
  })
  const { ref: noteFieldRef, ...noteFieldProps } = register('note', { required: true })

  const citableEvidence = useMemo(() => evidence ?? [], [evidence])
  const codeById = useMemo(
    () => new Map(citableEvidence.map((e) => [e.id, evidenceCode(e.sequenceNo)])),
    [citableEvidence]
  )
  const nonHypothesisEvidence = useMemo(
    () => citableEvidence.filter((e) => e.evidenceType !== 'HYPOTHESIS'),
    [citableEvidence]
  )

  const runResearchAction = (type: Exclude<EvidenceType, 'HYPOTHESIS'>) => {
    setActiveAction(type)
    setValue('evidenceType', type)
    noteRef.current?.focus()
    noteRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  const onSubmit = (data: FormValues) => {
    saveResearch.mutate(
      {
        note: data.note,
        evidenceType: data.evidenceType,
        sourceUrl: data.sourceUrl || undefined,
        sourceTitle: data.sourceTitle || undefined,
        occurredOn: data.occurredOn || undefined,
        confidence: data.confidence,
      },
      {
        onSuccess: () => {
          reset({ evidenceType: 'COMPANY_NEWS', confidence: 'MEDIUM' })
          setActiveAction(null)
        },
      }
    )
  }

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  const activePrompt = RESEARCH_ACTIONS.find((a) => a.type === activeAction)?.prompt

  return (
    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4}>
        <div className={styles.progressBanner}>
          <PhaseStepper currentPhase={engagement?.phase ?? 'CLIENT_INTELLIGENCE'} />
        </div>
      </Column>

      <Column lg={4} md={4} sm={4}>
        <Stack gap={6}>
          <div>
            <Heading className={styles.heading}>Client Intelligence</Heading>
            <p className={styles.subheading}>
              Choose a research action to guide your investigation, or log your own finding directly.
            </p>
          </div>

          <div className={styles.researchActions}>
            <h4 className={styles.researchActionsTitle}>Research Actions</h4>
            {RESEARCH_ACTIONS.map(({ type, label, icon: Icon }) => {
              const findingCount = nonHypothesisEvidence.filter((e) => e.evidenceType === type).length
              return (
                <button
                  key={type}
                  type="button"
                  className={`${styles.actionButton} ${activeAction === type ? styles.actionButtonActive : ''} ${findingCount > 0 ? styles.actionButtonComplete : ''}`}
                  onClick={() => runResearchAction(type)}
                >
                  {findingCount > 0 ? <CheckmarkFilled size={20} className={styles.actionButtonCheck} /> : <Icon size={20} />}
                  <span className={styles.actionButtonLabel}>{label}</span>
                  {findingCount > 0 && (
                    <span className={styles.actionButtonCount}>
                      {findingCount} finding{findingCount > 1 ? 's' : ''}
                    </span>
                  )}
                </button>
              )
            })}
          </div>

          <form onSubmit={handleSubmit(onSubmit)}>
            <Tile className={styles.formTile}>
              <Stack gap={4}>
                <h4 className={styles.formTitle}>
                  {activeAction ? RESEARCH_ACTIONS.find((a) => a.type === activeAction)?.label : 'Add Research Evidence'}
                </h4>

                {activePrompt && (
                  <p style={{ color: '#525252', fontSize: '0.8125rem', marginTop: '-0.5rem' }}>{activePrompt}</p>
                )}

                <Select
                  id="evidenceType"
                  labelText="Type"
                  {...register('evidenceType')}
                  onChange={(e) => {
                    register('evidenceType').onChange(e)
                    setActiveAction(null)
                  }}
                >
                  {EVIDENCE_TYPES.map((t) => (
                    <SelectItem key={t} value={t} text={t.replace(/_/g, ' ')} />
                  ))}
                </Select>

                <TextArea
                  id="note"
                  labelText="Evidence / Note"
                  rows={3}
                  invalid={Boolean(errors.note)}
                  invalidText="Required"
                  {...noteFieldProps}
                  ref={(el) => {
                    noteFieldRef(el)
                    noteRef.current = el
                  }}
                />

                <Accordion align="start">
                  <AccordionItem title="Source details (optional)">
                    <Stack gap={4}>
                      <Stack gap={4} orientation="horizontal" className={styles.sourceRow}>
                        <TextInput id="sourceTitle" labelText="Source title" {...register('sourceTitle')} />
                        <TextInput id="sourceUrl" labelText="Source URL" placeholder="https://…" {...register('sourceUrl')} />
                      </Stack>
                      <Stack gap={4} orientation="horizontal" className={styles.sourceRow}>
                        <Controller
                          control={control}
                          name="occurredOn"
                          render={({ field }) => (
                            <DatePicker
                              datePickerType="single"
                              dateFormat="Y-m-d"
                              onChange={(dates: Date[]) => {
                                const [d] = dates
                                field.onChange(d ? d.toISOString().slice(0, 10) : '')
                              }}
                            >
                              <DatePickerInput
                                id="occurredOn"
                                labelText="Date observed"
                                placeholder="yyyy-mm-dd"
                              />
                            </DatePicker>
                          )}
                        />
                        <Select id="confidence" labelText="Reliability / confidence" {...register('confidence')}>
                          {CONFIDENCE_LEVELS.map((c) => (
                            <SelectItem key={c} value={c} text={c} />
                          ))}
                        </Select>
                      </Stack>
                    </Stack>
                  </AccordionItem>
                </Accordion>

                <Button type="submit" renderIcon={Add} disabled={saveResearch.isPending}>
                  Add Evidence
                </Button>
              </Stack>
            </Tile>
          </form>

          <ResearchGateChecklist
            engagementId={engagementId!}
            onProceed={() => navigate(`/dashboard/engagements/${engagementId}/outreach`)}
          />
        </Stack>
      </Column>

      <Column lg={8} md={4} sm={4}>
        <div className={styles.evidenceBoard}>
          <div className={styles.evidenceBoardHeader}>
            <h3>Evidence Board</h3>
            <Tag type="blue" size="md">{nonHypothesisEvidence.length} items</Tag>
          </div>

          {nonHypothesisEvidence.length === 0 && (
            <Tile className={styles.emptyState}>
              <Search size={32} />
              <p>No evidence collected yet.</p>
              <Button
                kind="tertiary"
                size="sm"
                onClick={() => noteRef.current?.focus()}
              >
                Add your first evidence
              </Button>
            </Tile>
          )}

          <div className={styles.evidenceGrid}>
            {nonHypothesisEvidence.map((item) => (
              <EvidenceCard key={item.id} item={item} codeById={codeById} />
            ))}
          </div>
        </div>

        <HypothesisWorkspace evidence={citableEvidence} codeById={codeById} engagementId={engagementId!} />
      </Column>

      <Column lg={4} md={8} sm={4}>
        <ClientProfilePanel engagementId={engagementId!} />
      </Column>
    </Grid>
  )
}
