import { useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
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
  Accordion,
  AccordionItem,
  Tooltip,
  InlineNotification,
} from '@carbon/react'
import {
  Add, ArrowRight, Locked, Link as LinkIcon, Search,
  ChartLine, Devices, UserMultiple, Document, Information,
  CheckmarkFilled, CircleDash,
} from '@carbon/icons-react'
import { useForm, Controller } from 'react-hook-form'
import { useAnalyzeUserContext, useGenerateResearchIntelligence, useLeadIntelligence, useResearch, useResearchGateStatus, useCompleteResearch, useSaveResearch } from '@/api/hooks/useLeads'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConfidenceLevel, EvidenceType, IntelligenceField, ResearchArtifact, ResearchEvidence } from '@/api/types'
import styles from './ClientIntelligencePage.module.scss'
import PageHeader from '@/lifecycle/components/PageHeader'
import shell from '@/lifecycle/lifecycle.module.scss'

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

interface ExternalContextFormValues {
  context: string
}

// ─── Research Actions: guided prompts that steer the learner toward the right
// evidence category, instead of a blank "note" field (still requires the
// learner to enter their own real finding — no fabricated data is injected). ───
const RESEARCH_ACTIONS: { type: Exclude<EvidenceType, 'HYPOTHESIS'>; label: string; prompt: string; icon: typeof Search }[] = [
  { type: 'COMPANY_NEWS', label: 'Company News', prompt: 'Research this area to uncover relevant public signals and business pressure.', icon: Document },
  { type: 'STAKEHOLDER_PROFILE', label: 'Stakeholder Research', prompt: 'Research this area to identify decision makers, priorities and influence.', icon: UserMultiple },
  { type: 'FINANCIAL_SIGNAL', label: 'Financial Signals', prompt: 'Research this area to uncover commercial and funding indicators.', icon: ChartLine },
  { type: 'TECHNOLOGY_INDICATOR', label: 'Technology Research', prompt: 'Research this area to understand systems, architecture constraints and readiness.', icon: Devices },
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

function ResearchArtifactCard({
  artifact,
  onAdd,
  isAdding,
}: {
  artifact: ResearchArtifact
  onAdd: (artifact: ResearchArtifact) => void
  isAdding: boolean
}) {
  return (
    <Tile className={styles.evidenceCard}>
      <Stack gap={3}>
        <div className={styles.evidenceCardHeader}>
          <Tag type="purple" size="sm">{artifact.sourceType}</Tag>
          <Tag type={CONFIDENCE_TAG_TYPE[artifact.confidence]} size="sm">{artifact.confidence}</Tag>
          <Tag type={artifact.origin === 'USER_SUPPLIED' ? 'warm-gray' : 'cyan'} size="sm">
            {artifact.origin.replace(/_/g, ' ')}
          </Tag>
        </div>
        <div>
          <h5 style={{ color: '#161616', marginBottom: '0.375rem' }}>{artifact.title}</h5>
          <p className={styles.evidenceNote}>{artifact.summary}</p>
        </div>
        <p style={{ color: '#525252', fontSize: '0.75rem' }}>{artifact.relevanceRationale}</p>
        {artifact.correlatesWithEvidence.length > 0 && (
          <div className={styles.supportingEvidence}>
            <LinkIcon size={14} />
            <span>Correlates with {artifact.correlatesWithEvidence.join(', ')}</span>
          </div>
        )}
        <Button size="sm" kind="tertiary" disabled={isAdding} onClick={() => onAdd(artifact)}>
          Add to Evidence Board
        </Button>
      </Stack>
    </Tile>
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
/** The four rows rendered below; kept beside them so the two cannot drift. */
const GATE_REQUIREMENT_COUNT = 4

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

  /* Once every requirement is met the checklist has done its job. Keeping four
     satisfied rows on screen costs 120px of the client profile above it to
     restate a fact the single line already carries. Unmet, the list is the
     whole point and stays. */
  if (gate.ready) {
    return (
      <div className={styles.researchGate}>
        <p className={styles.gateReady}>
          <CheckmarkFilled size={16} /> Ready — all {GATE_REQUIREMENT_COUNT} requirements met
        </p>
        <Button
          renderIcon={ArrowRight}
          kind="secondary"
          disabled={completeResearch.isPending}
          onClick={handleProceed}
        >
          {completeResearch.isPending ? 'Advancing…' : 'Proceed to Outreach'}
        </Button>
      </div>
    )
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
  const { data: evidence, isLoading, isError } = useResearch(engagementId!)
  const saveResearch = useSaveResearch(engagementId!)
  const generateResearch = useGenerateResearchIntelligence(engagementId!)
  const analyzeUserContext = useAnalyzeUserContext(engagementId!)
  const noteRef = useRef<HTMLTextAreaElement | null>(null)
  const [activeAction, setActiveAction] = useState<Exclude<EvidenceType, 'HYPOTHESIS'> | null>(null)
  const [researchResults, setResearchResults] = useState<ResearchArtifact[]>([])

  const { register, handleSubmit, reset, setValue, control, formState: { errors } } = useForm<FormValues>({
    defaultValues: {
      evidenceType: 'COMPANY_NEWS',
      confidence: 'MEDIUM',
    },
  })
  const {
    register: registerExternalContext,
    handleSubmit: handleExternalContextSubmit,
    reset: resetExternalContext,
    formState: { errors: externalContextErrors },
  } = useForm<ExternalContextFormValues>()
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

  const selectResearchAction = (type: Exclude<EvidenceType, 'HYPOTHESIS'>) => {
    setActiveAction(type)
    setValue('evidenceType', type)
    setResearchResults([])
    generateResearch.reset()
    analyzeUserContext.reset()
  }

  const generateSelectedResearch = () => {
    if (!activeAction) return
    generateResearch.mutate(activeAction, { onSuccess: setResearchResults })
  }

  const addArtifactToEvidence = (artifact: ResearchArtifact) => {
    saveResearch.mutate(
      {
        note: artifact.summary,
        evidenceType: artifact.evidenceType,
        sourceTitle: artifact.title,
        occurredOn: artifact.publishedOn,
        confidence: artifact.confidence,
        origin: artifact.origin,
      },
      {
        onSuccess: () => {
          setResearchResults((items) => items.filter((item) => item.id !== artifact.id))
        },
      }
    )
  }

  const onExternalContextSubmit = (data: ExternalContextFormValues) => {
    analyzeUserContext.mutate(data.context, {
      onSuccess: (artifact) => {
        const inferredType = artifact.evidenceType === 'HYPOTHESIS' ? 'OTHER' : artifact.evidenceType
        setActiveAction(inferredType as Exclude<EvidenceType, 'HYPOTHESIS'>)
        setResearchResults([artifact])
        resetExternalContext()
      },
    })
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

  const activeResearchAction = RESEARCH_ACTIONS.find((a) => a.type === activeAction)
  const activePrompt = activeResearchAction?.prompt

  return (
    <>
    <PageHeader
      phase="CLIENT_INTELLIGENCE"
      description="Build evidence, reveal client intelligence and submit a grounded hypothesis before outreach unlocks."
      actions={<Tag type="blue" size="md">{nonHypothesisEvidence.length} evidence items</Tag>}
    />
    <Grid fullWidth className={`${styles.page} ${shell.fixedShellBody}`}>

      <Column lg={4} md={8} sm={4} className={shell.scrollPanel}>
        <aside className={styles.sideRail}>
          <div className={styles.researchActions}>
            <h4 className={styles.researchActionsTitle}>Research Actions</h4>
            {RESEARCH_ACTIONS.map(({ type, label, icon: Icon }) => {
              const findingCount = nonHypothesisEvidence.filter((e) => e.evidenceType === type).length
              return (
                <button
                  key={type}
                  type="button"
                  className={`${styles.actionButton} ${activeAction === type ? styles.actionButtonActive : ''} ${findingCount > 0 ? styles.actionButtonComplete : ''}`}
                  disabled={generateResearch.isPending || analyzeUserContext.isPending}
                  onClick={() => selectResearchAction(type)}
                >
                  {findingCount > 0 ? <CheckmarkFilled size={20} className={styles.actionButtonCheck} /> : <Icon size={20} />}
                  <span className={styles.actionButtonLabel}>{label}</span>
                  {findingCount > 0 && (
                    <span className={styles.actionButtonCount}>
                      {findingCount}
                      {/* The green check already says "done"; spelling out
                          "findings" beside it repeated that and cost 40px the
                          262px button did not have, which pushed the pill onto
                          a second line on two of the four actions and left the
                          rail ragged. The word stays for screen readers. */}
                      <span className="cds--visually-hidden">
                        {' '}finding{findingCount > 1 ? 's' : ''}
                      </span>
                    </span>
                  )}
                </button>
              )
            })}
          </div>

        </aside>
      </Column>

      <Column lg={7} md={8} sm={4} className={shell.scrollPanel}>
        <main className={styles.mainWorkspace}>
          <section className={styles.researchResultsPanel}>
            <div className={styles.panelHeader}>
              <div>
                <p className={styles.sectionEyebrow}>Research Results</p>
                <h3>{activeResearchAction?.label ?? 'Select a research action'}</h3>
              </div>
              {activeAction && <Tag type="cyan" size="sm">{activeAction.replace(/_/g, ' ')}</Tag>}
            </div>
            <p className={styles.panelDescription}>
              {activeResearchAction
                ? activeResearchAction.prompt
                : 'Choose an intelligence area to start a controlled research workflow.'}
            </p>

            {activeResearchAction && (
              <div className={styles.researchOptions}>
                <Tile className={styles.researchOptionCard}>
                  <Stack gap={3}>
                    <div>
                      <h4>AI-generated scenario intelligence</h4>
                      <p>
                        Generate controlled artefacts from scenario-approved facts. These results stay consistent with the canonical case truth.
                      </p>
                    </div>
                    <Button
                      size="sm"
                      onClick={generateSelectedResearch}
                      disabled={generateResearch.isPending || analyzeUserContext.isPending}
                    >
                      {generateResearch.isPending ? 'Generating...' : `Generate ${activeResearchAction.label}`}
                    </Button>
                  </Stack>
                </Tile>

                <form onSubmit={handleExternalContextSubmit(onExternalContextSubmit)}>
                  <Tile className={styles.researchOptionCard}>
                    <Stack gap={3}>
                      <div>
                        <h4>Add external context</h4>
                        <p>
                          Paste a note, article excerpt or observation. AI will correlate it as unverified intelligence without overwriting scenario truth.
                        </p>
                      </div>
                      <TextArea
                        id="external-context"
                        labelText="External context"
                        rows={3}
                        invalid={Boolean(externalContextErrors.context)}
                        invalidText="Required"
                        {...registerExternalContext('context', { required: true })}
                      />
                      <Button type="submit" size="sm" kind="tertiary" disabled={analyzeUserContext.isPending || generateResearch.isPending}>
                        {analyzeUserContext.isPending ? 'Analysing...' : 'Analyse Context'}
                      </Button>
                    </Stack>
                  </Tile>
                </form>
              </div>
            )}

            {generateResearch.isPending && (
              <div className={styles.researchLoading}>
                <div className={styles.researchLoadingPulse} />
                <div>
                  <strong>Preparing scenario-safe intelligence...</strong>
                  <span>Fast fallback will return canonical artefacts if the AI gateway is slow.</span>
                </div>
              </div>
            )}

            {generateResearch.isError && (
              <InlineNotification
                kind="error"
                lowContrast
                title="Research could not be generated"
                subtitle="The intelligence service returned an error. Retry this action after the API reloads."
                hideCloseButton
                className={styles.researchError}
              />
            )}

            {!activeResearchAction && (
              <div className={styles.resultsEmpty}>
                <Search size={24} />
                <span>Select a research action to choose how you want to gather intelligence.</span>
              </div>
            )}

            {activeResearchAction && !generateResearch.isPending && !generateResearch.isError && !analyzeUserContext.isPending && researchResults.length === 0 && (
              <div className={styles.resultsEmpty}>
                <Search size={24} />
                <span>Use AI generation or external context analysis to create reviewable intelligence.</span>
              </div>
            )}

            <div className={styles.artifactGrid}>
              {researchResults.map((artifact) => (
                <ResearchArtifactCard
                  key={artifact.id}
                  artifact={artifact}
                  onAdd={addArtifactToEvidence}
                  isAdding={saveResearch.isPending}
                />
              ))}
            </div>
          </section>

          <div className={styles.evidenceBoard}>
            <div className={styles.evidenceBoardHeader}>
              <div>
                <p className={styles.sectionEyebrow}>Evidence Board</p>
                <h3>Collected Evidence</h3>
              </div>
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
        </main>
      </Column>

      <Column lg={5} md={8} sm={4} className={`${styles.gateColumn} ${shell.fixedShellFrame}`}>
        <aside className={`${styles.decisionRail} ${shell.scrollPanel}`}>
          <ClientProfilePanel engagementId={engagementId!} />
          <HypothesisWorkspace evidence={citableEvidence} codeById={codeById} engagementId={engagementId!} />
          <form onSubmit={handleSubmit(onSubmit)}>
            <Tile className={styles.formTile}>
              <Stack gap={4}>
                <h4 className={styles.formTitle}>
                  Manual Evidence Entry
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
        </aside>

        {/* Outside the scrolling rail, not merely sticky inside it. As a sticky
            last-ish child it scrolled away as soon as the manual entry form
            below it came into view — the one block that must never require
            scrolling to find. */}
        <ResearchGateChecklist
          engagementId={engagementId!}
          onProceed={() => navigate(`/dashboard/engagements/${engagementId}/outreach`)}
        />
      </Column>
    </Grid>
    </>
  )
}
