import { useMemo, useState } from 'react'
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
  Modal,
  RadioButton,
  RadioButtonGroup,
  InlineNotification,
} from '@carbon/react'
import {
  Add, ArrowRight, Locked, Link as LinkIcon, Search,
  ChartLine, Devices, UserMultiple, Document,
  CheckmarkFilled, CircleDash, ChevronLeft, ChevronRight,
} from '@carbon/icons-react'
import { useForm, Controller } from 'react-hook-form'
import { useResearch, useResearchGateStatus, useCompleteResearch, useResearchSourceDeck, useSaveResearch } from '@/api/hooks/useLeads'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConfidenceLevel, EvidenceType, EvidenceVerificationStatus, ReasoningLane, ResearchArtifact, ResearchEvidence } from '@/api/types'
import styles from './ClientIntelligencePage.module.scss'
import { PHASE_LABEL } from '@/lifecycle/phases'
import ObjectiveTourProvider from '@/components/shared/ObjectiveTourProvider'

const CLIENT_INTELLIGENCE_OBJECTIVES = [
  {
    id: 'readiness',
    objective: 'Understand outreach readiness',
    description: 'These requirements show what you need to complete before you can move into outreach.',
    targets: ['.objective-readiness'],
  },
  {
    id: 'evidence',
    objective: 'Build your evidence base',
    description: 'Collect enough evidence and add your findings to the evidence board. The evidence requirement and evidence board are highlighted together.',
    targets: ['.objective-evidence', '.objective-evidence-board'],
  },
  {
    id: 'stakeholder',
    objective: 'Identify your stakeholder',
    description: 'Research and identify a relevant stakeholder. Both the requirement and stakeholder research area are highlighted together.',
    targets: ['.objective-stakeholder-research'],
  },
  {
    id: 'hypothesis',
    objective: 'Form a grounded hypothesis',
    description: 'Use the evidence you collected to explain the client’s underlying problem and likely business impact.',
    targets: ['.objective-hypothesis'],
  },
]

const EVIDENCE_TYPES: Exclude<EvidenceType, 'HYPOTHESIS'>[] = [
  'COMPANY_NEWS', 'FINANCIAL_SIGNAL', 'TECHNOLOGY_INDICATOR',
  'STAKEHOLDER_PROFILE', 'MARKET_TREND', 'OTHER',
]

const CONFIDENCE_LEVELS: ConfidenceLevel[] = ['LOW', 'MEDIUM', 'HIGH']
const VERIFICATION_STATUSES: EvidenceVerificationStatus[] = ['VERIFIED', 'CORROBORATED', 'UNVERIFIED', 'CONTRADICTED']

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

const REASONING_LANES: Array<{ value: ReasoningLane; label: string }> = [
  { value: 'SYMPTOM', label: 'Observable symptom' },
  { value: 'LIKELY_CAUSE', label: 'Likely cause' },
  { value: 'STAKEHOLDER_CONSTRAINT', label: 'Stakeholder constraint' },
  { value: 'BUSINESS_IMPACT', label: 'Business impact' },
  { value: 'OPEN_QUESTION', label: 'Open question to validate' },
]

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
      <div className={styles.evidenceCardHeader}>
        <span className={styles.evidenceCode}>{evidenceCode(item.sequenceNo)}</span>
        <Tag type={CONFIDENCE_TAG_TYPE[item.confidence]} size="sm">{item.confidence}</Tag>
      </div>
      <p className={styles.evidenceTitle}>{item.sourceTitle || item.evidenceType.replace(/_/g, ' ')}</p>
      <p className={styles.evidenceNote}>{item.note}</p>
      <div className={styles.evidenceCardFooter}>
        <Tag type="blue" size="sm">{item.evidenceType.replace(/_/g, ' ')}</Tag>
        {item.reasoningLane && <Tag type="purple" size="sm">{item.reasoningLane.replace(/_/g, ' ')}</Tag>}
        <Tag type={item.relevanceScore >= 70 ? 'green' : item.relevanceScore >= 45 ? 'warm-gray' : 'red'} size="sm">
          {item.relevanceScore}% relevant
        </Tag>
        {item.supportingEvidenceIds.length > 0 && (
          <span className={styles.compactEvidenceSupport}>
            <LinkIcon size={12} /> {item.supportingEvidenceIds.map((id) => codeById.get(id) ?? '?').join(', ')}
          </span>
        )}
      </div>
    </Tile>
  )
}

function SourceDocument({ artifact, onSelectionChange }: { artifact: ResearchArtifact; onSelectionChange: (text: string) => void }) {
  const blocks = artifact.blocks?.length
    ? artifact.blocks
    : [{ id: 'summary', type: 'PARAGRAPH' as const, content: artifact.summary, attribution: null }]
  const sourceKind = artifact.evidenceType.replace(/_/g, ' ').toLowerCase()
  const templateClass = artifact.evidenceType === 'STAKEHOLDER_PROFILE'
    ? styles.stakeholderTemplate
    : artifact.evidenceType === 'FINANCIAL_SIGNAL'
      ? styles.financialTemplate
      : artifact.evidenceType === 'TECHNOLOGY_INDICATOR'
        ? styles.technologyTemplate
        : styles.newspaperTemplate
  const isNewspaper = artifact.evidenceType === 'COMPANY_NEWS'
  const isStakeholder = artifact.evidenceType === 'STAKEHOLDER_PROFILE'
  const isFinancial = artifact.evidenceType === 'FINANCIAL_SIGNAL'
  const isTechnology = artifact.evidenceType === 'TECHNOLOGY_INDICATOR'
  const documentLabel = isNewspaper
    ? 'The Client Observer'
    : isStakeholder
      ? 'Stakeholder intelligence dossier'
      : isFinancial
        ? 'Commercial intelligence note'
        : 'Technology due diligence brief'

  const captureSelection = () => {
    const selected = window.getSelection()?.toString().replace(/\s+/g, ' ').trim() ?? ''
    onSelectionChange(selected.length >= 8 ? selected : '')
  }

  return (
    <article className={`${styles.sourceDocument} ${templateClass}`} onMouseUp={captureSelection} onKeyUp={captureSelection}>
      {isNewspaper && <div className={styles.documentMasthead}><strong>{documentLabel}</strong><span>Client intelligence edition</span><span>{artifact.publishedOn}</span></div>}
      {!isNewspaper && <p className={styles.sourceTemplateLabel}>{documentLabel}</p>}
      <header className={styles.sourceDocumentHeader}>
        <div>
          <p className={styles.sectionEyebrow}>{sourceKind} · {artifact.sourceType}</p>
          <h3>{artifact.title}</h3>
          <p className={styles.sourceDocumentDek}>{artifact.summary}</p>
          <p className={styles.sourceDocumentMeta}>Scenario-curated source · {artifact.publishedOn} · {artifact.confidence.toLowerCase()} reliability</p>
        </div>
        <Tag type={artifact.relevanceScore >= 70 ? 'green' : artifact.relevanceScore >= 45 ? 'warm-gray' : 'red'}>
          {artifact.relevanceScore}% relevant
        </Tag>
      </header>
      {isStakeholder && (
        <div className={styles.dossierStrip}>
          <span aria-hidden="true">{artifact.title.slice(0, 1).toUpperCase()}</span>
          <div><strong>Stakeholder dossier</strong><p>Review priorities, constraints and influence signals before drawing a conclusion.</p></div>
          <Tag type="purple" size="sm">Influence signal</Tag>
        </div>
      )}
      {isStakeholder && <div className={styles.documentLens}><span>Read for: stated priority</span><span>Influence: validate</span><span>Decision role: unconfirmed</span></div>}
      {isFinancial && <div className={styles.signalBanner}><span>Financial analysis</span><strong>Validate commercial materiality before assuming budget.</strong></div>}
      {isTechnology && <div className={styles.signalBanner}><span>Technology briefing</span><strong>Separate current-state constraints from assumptions about the solution.</strong></div>}
      <div className={styles.sourceDocumentBody}>
        {blocks.map((block) => {
          if (block.type === 'QUOTE') return <blockquote key={block.id}>{block.content}{block.attribution && <cite>{block.attribution}</cite>}</blockquote>
          if (block.type === 'METRIC') return <div key={block.id} className={styles.sourceMetric}>{block.content}{block.attribution && <small>{block.attribution}</small>}</div>
          if (block.type === 'CAPTION') return <p key={block.id} className={styles.sourceCaption}>{block.content}</p>
          return <p key={block.id}>{block.content}</p>
        })}
      </div>
      <p className={styles.selectionInstruction}>Highlight a meaningful sentence or phrase. Your selected text stays traceable to this source.</p>
    </article>
  )
}

/** Requirement row for {@link ResearchGateChecklist} — met (✓ blue) or unmet (○ gray). */
function SourceDeck({
  sources,
  onOpenSource,
  isLoading,
}: {
  sources: ResearchArtifact[]
  onOpenSource: (source: ResearchArtifact) => void
  isLoading: boolean
}) {
  if (isLoading && sources.length === 0) {
    return <div className={styles.researchLoading}><div className={styles.researchLoadingPulse} /><span>Preparing your scenario source deck...</span></div>
  }

  if (sources.length === 0) {
    return <div className={styles.workspaceEmpty}><Search size={24} /><span>No sources are available for this research area yet.</span></div>
  }

  return (
    <section className={styles.sourceDeck} aria-label="Scenario research source deck">
      <header className={styles.sourceDeckHeader}>
        <div className={styles.sourceDeckTitle}><Document size={24} /><div><h2>Source Deck</h2><p>Read the source, highlight a signal, then explain why it matters.</p></div></div>
        <span className={styles.sourceDeckCount}>{sources.length} sources</span>
      </header>
      <div className={styles.sourceDeckContent}>
        <div className={styles.sourceDeckGrid} aria-label="Available research sources">
          {sources.map((source, index) => (
            <button key={source.id} type="button" className={styles.sourceDeckCard} onClick={() => onOpenSource(source)}>
              <span className={`${styles.sourceDeckVisual} ${styles[`sourceDeckVisual${source.evidenceType}`] ?? ''}`} aria-hidden="true"><Document size={22} /><b>{index + 1}</b></span>
              <span className={styles.sourceDeckCardCopy}>
                <span className={styles.sourceDeckCardMeta}><Tag type={source.relevanceScore >= 70 ? 'green' : 'warm-gray'} size="sm">{source.confidence.toLowerCase()} trust</Tag><small>{source.publishedOn}</small></span>
                <strong>{source.title}</strong><small>{source.sourceType}</small><span className={styles.openSourceLabel}>Open document <ArrowRight size={16} /></span>
              </span>
            </button>
          ))}
        </div>
      </div>
      <footer className={styles.sourceDeckFooter}><span><b>1</b> Browse sources</span><span><b>2</b> Highlight evidence</span><span><b>3</b> Add to your board</span></footer>
    </section>
  )
}

function GateRequirement({ met, label, className }: { met: boolean; label: string; className?: string }) {
  return (
    <div className={`${styles.gateRequirement} ${className ?? ''}`}>
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
        <GateRequirement
          met={gate.hasStakeholderEvidence}
          label="Stakeholder evidence identified"
        />
        <GateRequirement
          met={gate.coverageCount >= gate.requiredCoverageCount}
          label={`${gate.requiredCoverageCount} research areas covered (${gate.coverageCount}/${gate.requiredCoverageCount})`}
        />
        <GateRequirement
          met={gate.groundedHypothesis}
          label="Grounded hypothesis submitted"
        />
        <GateRequirement
          met={gate.confidencePercent >= gate.requiredConfidencePercent}
          label={`Research confidence at least ${gate.requiredConfidencePercent}% (${gate.confidencePercent}%)`}
        />
      </Stack>

      {gate.coaching?.length > 0 && (
        <div className={styles.gateCoaching}>
          <p className={styles.sectionEyebrow}>Next best action</p>
          <p>{gate.coaching[0]}</p>
        </div>
      )}

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
  const [citationPage, setCitationPage] = useState(0)
  const hypotheses = useMemo(
    () => (evidence ?? []).filter((e) => e.evidenceType === 'HYPOTHESIS').sort((a, b) => b.sequenceNo - a.sequenceNo),
    [evidence]
  )
  const citableEvidence = useMemo(() => (evidence ?? []).filter((e) => e.evidenceType !== 'HYPOTHESIS'), [evidence])
  const citationPageSize = 4
  const visibleCitations = citableEvidence.slice(citationPage * citationPageSize, (citationPage + 1) * citationPageSize)
  const citationPageCount = Math.max(1, Math.ceil(citableEvidence.length / citationPageSize))

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
          setCitationPage(0)
        },
      }
    )
  }

  return (
    <div className={`${styles.hypothesisWorkspace} objective-hypothesis`}>
      <div className={styles.hypothesisWorkspaceHeader}>
        <h3>Hypothesis</h3>
        <Button kind="ghost" size="sm" onClick={() => setComposing(true)}>
          {hypotheses.length > 0 ? 'Refine hypothesis' : 'Add hypothesis'}
        </Button>
      </div>

      {hypotheses.length === 0 && !composing && (
        <p className={styles.hypothesisEmpty}>
          No hypothesis yet. Once you've gathered a few pieces of evidence, form a hypothesis
          about the client's underlying problem.
        </p>
      )}

      {hypotheses.slice(0, 1).map((h) => (
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

      <Modal open={composing} modalHeading="Build a grounded hypothesis" primaryButtonText={saveResearch.isPending ? 'Saving...' : 'Save hypothesis'} secondaryButtonText="Cancel" primaryButtonDisabled={saveResearch.isPending} onRequestClose={() => setComposing(false)} onRequestSubmit={handleSubmit(onSubmit)}>
        <form onSubmit={handleSubmit(onSubmit)} className={styles.hypothesisForm}>
          <TextArea id="hypothesis-statement" labelText="Hypothesis statement" placeholder="State the observed problem, likely cause and business impact." rows={3} invalid={Boolean(errors.hypothesis)} invalidText="Required" {...register('hypothesis', { required: true })} />
          {citableEvidence.length > 0 && (
            <div>
              <div className={styles.modalSectionHeader}><p className={styles.linkLabel}>Supporting evidence</p>{citableEvidence.length > citationPageSize && <div className={styles.pager}><Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronLeft} iconDescription="Previous citations" disabled={citationPage === 0} onClick={() => setCitationPage((page) => page - 1)} /><span>{citationPage + 1} / {citationPageCount}</span><Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronRight} iconDescription="Next citations" disabled={citationPage >= citationPageCount - 1} onClick={() => setCitationPage((page) => page + 1)} /></div>}</div>
              <div className={styles.citationGrid}>{visibleCitations.map((e) => <Controller key={e.id} control={control} name="supportingEvidenceIds" render={({ field }) => <Checkbox id={`support-${e.id}`} labelText={`${evidenceCode(e.sequenceNo)} — ${e.note.slice(0, 74)}`} checked={field.value?.includes(e.id) ?? false} onChange={(_, { checked }) => { const current = field.value ?? []; field.onChange(checked ? [...current, e.id] : current.filter((id) => id !== e.id)) }} />} />)}</div>
            </div>
          )}
          <Controller
            control={control}
            name="confidence"
            render={({ field }) => (
              <RadioButtonGroup
                legendText="How confident are you?"
                name="hypothesis-confidence"
                orientation="horizontal"
                valueSelected={field.value}
                onChange={(value) => field.onChange(value as ConfidenceLevel)}
              >
                {CONFIDENCE_LEVELS.map((confidence) => (
                  <RadioButton key={confidence} id={`hypothesis-confidence-${confidence}`} value={confidence} labelText={confidence} />
                ))}
              </RadioButtonGroup>
            )}
          />
        </form>
      </Modal>
    </div>
  )
}

export default function ClientIntelligencePage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()
  const { data: evidence, isLoading, isError } = useResearch(engagementId!)
  const saveResearch = useSaveResearch(engagementId!)
  const { data: gate } = useResearchGateStatus(engagementId!)
  const [activeAction, setActiveAction] = useState<Exclude<EvidenceType, 'HYPOTHESIS'> | null>('COMPANY_NEWS')
  const [readerArtifact, setReaderArtifact] = useState<ResearchArtifact | null>(null)
  const [evidencePage, setEvidencePage] = useState(0)
  const [manualEvidenceOpen, setManualEvidenceOpen] = useState(false)
  const [reviewingArtifact, setReviewingArtifact] = useState<ResearchArtifact | null>(null)
  const [selectedSnippet, setSelectedSnippet] = useState('')
  const [reviewTakeaway, setReviewTakeaway] = useState('')
  const [reviewLane, setReviewLane] = useState<ReasoningLane>('SYMPTOM')
  const [reviewConfidence, setReviewConfidence] = useState<ConfidenceLevel>('MEDIUM')
  const [reviewVerification, setReviewVerification] = useState<EvidenceVerificationStatus>('CORROBORATED')
  const sourceDeck = useResearchSourceDeck(engagementId!)

  const { register, handleSubmit, reset, setValue, formState: { errors } } = useForm<FormValues>({
    defaultValues: {
      evidenceType: 'COMPANY_NEWS',
      confidence: 'MEDIUM',
    },
  })
  const citableEvidence = useMemo(() => evidence ?? [], [evidence])
  const codeById = useMemo(
    () => new Map(citableEvidence.map((e) => [e.id, evidenceCode(e.sequenceNo)])),
    [citableEvidence]
  )
  const nonHypothesisEvidence = useMemo(
    () => citableEvidence.filter((e) => e.evidenceType !== 'HYPOTHESIS'),
    [citableEvidence]
  )
  const evidencePageSize = 4
  const visibleEvidence = nonHypothesisEvidence.slice(evidencePage * evidencePageSize, (evidencePage + 1) * evidencePageSize)
  const evidencePageCount = Math.max(1, Math.ceil(nonHypothesisEvidence.length / evidencePageSize))
  const activeSources = useMemo(() => {
    if (!activeAction) return []
    const scenarioSources = sourceDeck.data?.sourcesByType[activeAction] ?? []
    return scenarioSources
  }, [activeAction, sourceDeck.data])
  const readerIndex = readerArtifact ? activeSources.findIndex((source) => source.id === readerArtifact.id) : -1
  const readinessCompleteCount = [
    gate ? gate.evidenceCount >= gate.requiredEvidenceCount : false,
    gate?.hasStakeholderEvidence ?? false,
    gate ? gate.coverageCount >= gate.requiredCoverageCount : false,
    gate?.groundedHypothesis ?? false,
    gate ? gate.confidencePercent >= gate.requiredConfidencePercent : false,
  ].filter(Boolean).length

  const selectResearchAction = (type: Exclude<EvidenceType, 'HYPOTHESIS'>) => {
    if (type === activeAction) return
    setActiveAction(type)
    setValue('evidenceType', type)
    setReaderArtifact(null)
    setSelectedSnippet('')
  }

  const beginEvidenceAssessment = (artifact: ResearchArtifact) => {
    setReviewingArtifact(artifact)
    setReviewTakeaway('')
    setReviewLane('SYMPTOM')
    setReviewConfidence(artifact.confidence)
    setReviewVerification(artifact.origin === 'SCENARIO_CURATED' ? 'CORROBORATED' : 'UNVERIFIED')
  }


  const saveReviewedArtifact = () => {
    if (!reviewingArtifact || !selectedSnippet || !reviewTakeaway.trim()) return
    saveResearch.mutate(
      {
        note: `${selectedSnippet}\n\nConsulting takeaway: ${reviewTakeaway.trim()}`,
        evidenceType: reviewingArtifact.evidenceType,
        sourceTitle: reviewingArtifact.title,
        occurredOn: reviewingArtifact.publishedOn,
        confidence: reviewConfidence,
        origin: reviewingArtifact.origin,
        verificationStatus: reviewVerification,
        relevanceScore: reviewingArtifact.relevanceScore,
        reasoningLane: reviewLane,
      },
      {
        onSuccess: () => {
          setEvidencePage(0)
          setReviewingArtifact(null)
          setSelectedSnippet('')
        },
      }
    )
  }

  const closeSourceReview = () => {
    setReviewingArtifact(null)
    setSelectedSnippet('')
  }

  const openSourceReader = (artifact: ResearchArtifact) => {
    setReaderArtifact(artifact)
    setSelectedSnippet('')
  }

  const moveReader = (direction: -1 | 1) => {
    if (readerIndex < 0 || activeSources.length < 2) return
    const nextIndex = (readerIndex + direction + activeSources.length) % activeSources.length
    setReaderArtifact(activeSources[nextIndex])
    setSelectedSnippet('')
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
          setActiveAction(data.evidenceType === 'HYPOTHESIS' ? 'OTHER' : data.evidenceType)
          setManualEvidenceOpen(false)
          setEvidencePage(0)
        },
      }
    )
  }

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  const activeResearchAction = RESEARCH_ACTIONS.find((a) => a.type === activeAction)

  return (
    <ObjectiveTourProvider objectives={CLIENT_INTELLIGENCE_OBJECTIVES}>
    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4} className={styles.headerColumn}>
        <nav className={styles.breadcrumbs} aria-label="Workflow path">
          <span>Research workflow</span><ChevronRight size={16} />
          <span>Build evidence and test a hypothesis</span><ChevronRight size={16} />
          <strong>Research the client</strong>
        </nav>
        <header className={styles.pageHeader}>
          <div className={styles.titleBlock}>
            <div className={styles.titleIcon}><Search size={26} /></div>
            <div>
              <Heading className={styles.heading}>{PHASE_LABEL.CLIENT_INTELLIGENCE}</Heading>
              <p className={styles.subheading}>Build evidence, reveal client intelligence and submit a grounded hypothesis before outreach.</p>
            </div>
          </div>
          <div className={styles.metricRow}>
            <div className={styles.metricTile}><span>Evidence items</span><strong>{nonHypothesisEvidence.length}<small> / {gate?.requiredEvidenceCount ?? 2}</small></strong></div>
            <div className={styles.metricTile}><span>Research quality</span><strong>{gate?.confidencePercent ?? 0}% <small>{gate && gate.confidencePercent >= gate.requiredConfidencePercent ? 'On track' : 'Building'}</small></strong></div>
            <div className={styles.metricTile}><span>Readiness</span><strong>{Math.round((readinessCompleteCount / 5) * 100)}% <small>{gate?.ready ? 'Ready' : 'In progress'}</small></strong></div>
          </div>
        </header>

        <section className={`${styles.readinessBand} objective-readiness`} aria-label="Outreach readiness">
          <div className={styles.readinessTitle}><span>Outreach readiness</span><strong>{readinessCompleteCount} of 5 complete</strong></div>
          <GateRequirement met={(gate?.evidenceCount ?? 0) >= (gate?.requiredEvidenceCount ?? 2)} label={`${gate?.requiredEvidenceCount ?? 2} evidence items`} />
          <GateRequirement met={gate?.hasStakeholderEvidence ?? false} label="Stakeholder identified" />
          <GateRequirement met={(gate?.coverageCount ?? 0) >= (gate?.requiredCoverageCount ?? 2)} label={`${gate?.requiredCoverageCount ?? 2} areas covered`} />
          <GateRequirement met={gate?.groundedHypothesis ?? false} label="Grounded hypothesis" />
          <GateRequirement met={(gate?.confidencePercent ?? 0) >= (gate?.requiredConfidencePercent ?? 40)} label={`${gate?.requiredConfidencePercent ?? 40}% confidence`} />
        </section>
      </Column>

      <Column lg={3} md={3} sm={4} className={styles.workColumn}>
        <aside className={`${styles.researchActions} objective-evidence`}>
          <h2>Research areas</h2>
          {RESEARCH_ACTIONS.map(({ type, label, prompt, icon: Icon }) => {
            const findingCount = nonHypothesisEvidence.filter((e) => e.evidenceType === type).length
            return (
              <button key={type} type="button" className={`${styles.actionButton} ${type === 'STAKEHOLDER_PROFILE' ? 'objective-stakeholder-research' : ''} ${activeAction === type ? styles.actionButtonActive : ''}`} disabled={sourceDeck.isFetching} onClick={() => selectResearchAction(type)}>
                <Icon size={22} /><span className={styles.actionButtonLabel}><strong>{label}</strong><small>{prompt.replace('Research this area to ', '')}</small></span>
                {findingCount > 0 && <span className={styles.actionButtonCount}>{findingCount}</span>}
              </button>
            )
          })}
        </aside>
      </Column>

      <Column lg={9} md={5} sm={4} className={styles.workColumn}>
        <main className={styles.workspace}>
          <section className={styles.researchWorkspace}>
            <div className={styles.workspaceHeading}><div><p className={styles.sectionEyebrow}>Research workspace</p><h2>{activeResearchAction?.label ?? 'Choose a research area'}</h2></div>{activeAction && <Tag type="blue" size="sm">{activeAction.replace(/_/g, ' ')}</Tag>}</div>
            {activeResearchAction ? <SourceDeck sources={activeSources} onOpenSource={openSourceReader} isLoading={sourceDeck.isFetching} /> : <div className={styles.workspaceEmpty}><Search size={24} /><span>Select a research area to begin a controlled investigation.</span></div>}
            {sourceDeck.isError && <InlineNotification kind="error" lowContrast title="Sources could not be opened" subtitle="Check your connection, then retry. Your existing evidence is unchanged." hideCloseButton className={styles.researchError} />}
          </section>

          <section className={`${styles.evidenceBoard} objective-evidence-board`}>
            <div className={styles.compactSectionHeader}><div><p className={styles.sectionEyebrow}>Evidence board</p><h2>Collected evidence ({nonHypothesisEvidence.length})</h2></div><div className={styles.evidenceTools}><Button kind="tertiary" size="sm" renderIcon={Add} onClick={() => setManualEvidenceOpen(true)}>Add source</Button>{nonHypothesisEvidence.length > evidencePageSize && <div className={styles.pager}><Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronLeft} iconDescription="Previous evidence" disabled={evidencePage === 0} onClick={() => setEvidencePage((page) => page - 1)} /><span>{evidencePage + 1} / {evidencePageCount}</span><Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronRight} iconDescription="Next evidence" disabled={evidencePage >= evidencePageCount - 1} onClick={() => setEvidencePage((page) => page + 1)} /></div>}</div></div>
            {nonHypothesisEvidence.length === 0 ? <div className={styles.evidenceEmpty}><Search size={22} /><span>Generate or add a source to begin building your evidence board.</span></div> : <div className={styles.evidenceGrid}>{visibleEvidence.map((item) => <EvidenceCard key={item.id} item={item} codeById={codeById} />)}</div>}
          </section>
        </main>
      </Column>

      <Column lg={4} md={8} sm={4} className={styles.workColumn}>
        <aside className={styles.decisionRail}>
          <HypothesisWorkspace evidence={citableEvidence} codeById={codeById} engagementId={engagementId!} />
          <ResearchGateChecklist engagementId={engagementId!} onProceed={() => navigate(`/dashboard/engagements/${engagementId}/outreach`)} />
        </aside>
      </Column>

      <Modal open={manualEvidenceOpen} modalHeading="Add a source to the evidence board" primaryButtonText={saveResearch.isPending ? 'Saving...' : 'Add evidence'} secondaryButtonText="Cancel" onRequestClose={() => setManualEvidenceOpen(false)} onRequestSubmit={handleSubmit(onSubmit)} primaryButtonDisabled={saveResearch.isPending}>
        <form onSubmit={handleSubmit(onSubmit)} className={styles.manualEvidenceForm}>
          <Select id="evidenceType" labelText="Research area" {...register('evidenceType')} onChange={(event) => { register('evidenceType').onChange(event); setActiveAction(null) }}>{EVIDENCE_TYPES.map((type) => <SelectItem key={type} value={type} text={type.replace(/_/g, ' ')} />)}</Select>
          <TextArea id="note" labelText="Finding" rows={3} invalid={Boolean(errors.note)} invalidText="A finding is required" {...register('note', { required: true })} />
          <div className={styles.sourceInputs}><TextInput id="sourceTitle" labelText="Source title" {...register('sourceTitle')} /><Select id="confidence" labelText="Reliability" {...register('confidence')}>{CONFIDENCE_LEVELS.map((confidence) => <SelectItem key={confidence} value={confidence} text={confidence} />)}</Select></div>
          <TextInput id="sourceUrl" labelText="Source URL (optional)" placeholder="https://" {...register('sourceUrl')} />
        </form>
      </Modal>
      <Modal className={styles.readerModal} open={Boolean(readerArtifact)} modalHeading="Source reader" primaryButtonText="Close source" onRequestClose={() => setReaderArtifact(null)} onRequestSubmit={() => setReaderArtifact(null)} size="lg" isFullWidth>
        {readerArtifact && <div className={styles.readerModalBody}>
          <div className={styles.readerNavigator}>
            <Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronLeft} iconDescription="Previous source" disabled={activeSources.length < 2} onClick={() => moveReader(-1)} />
            <span>Source {readerIndex + 1} of {activeSources.length}</span>
            <Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronRight} iconDescription="Next source" disabled={activeSources.length < 2} onClick={() => moveReader(1)} />
          </div>
          <SourceDocument artifact={readerArtifact} onSelectionChange={setSelectedSnippet} />
          {selectedSnippet && <div className={styles.readerSelectionToolbar}><div><Tag type="purple">Evidence selected</Tag><span>{selectedSnippet.length > 170 ? `${selectedSnippet.slice(0, 170)}...` : selectedSnippet}</span></div><Button size="sm" renderIcon={Add} onClick={() => { beginEvidenceAssessment(readerArtifact); setReaderArtifact(null) }}>Assess evidence</Button></div>}
        </div>}
      </Modal>
      <Modal open={Boolean(reviewingArtifact)} modalHeading="Assess selected evidence" primaryButtonText={saveResearch.isPending ? 'Saving...' : 'Add evidence'} secondaryButtonText="Cancel" primaryButtonDisabled={saveResearch.isPending || !selectedSnippet || !reviewTakeaway.trim()} onRequestClose={closeSourceReview} onSecondarySubmit={closeSourceReview} onRequestSubmit={saveReviewedArtifact} size="lg">
        {reviewingArtifact && <Stack gap={5}>
          <div className={styles.selectedEvidencePreview}><p className={styles.sectionEyebrow}>Selected from {reviewingArtifact.title}</p><p>{selectedSnippet}</p></div>
          <div><p className={styles.sectionEyebrow}>{reviewingArtifact.sourceType} · {reviewingArtifact.confidence} reliability</p><h3>{reviewingArtifact.title}</h3><p>{reviewingArtifact.summary}</p></div>
          <Select id="source-reasoning-lane" labelText="What does this source help you explain?" value={reviewLane} onChange={(event) => setReviewLane(event.target.value as ReasoningLane)}>{REASONING_LANES.map((lane) => <SelectItem key={lane.value} value={lane.value} text={lane.label} />)}</Select>
          <div className={styles.sourceAssessmentGrid}>
            <Select id="source-confidence" labelText="Your confidence" value={reviewConfidence} onChange={(event) => setReviewConfidence(event.target.value as ConfidenceLevel)}>{CONFIDENCE_LEVELS.map((confidence) => <SelectItem key={confidence} value={confidence} text={confidence} />)}</Select>
            <Select id="source-verification" labelText="Verification status" value={reviewVerification} onChange={(event) => setReviewVerification(event.target.value as EvidenceVerificationStatus)}>{VERIFICATION_STATUSES.map((status) => <SelectItem key={status} value={status} text={status.replace(/_/g, ' ')} />)}</Select>
          </div>
          <TextArea id="source-takeaway" labelText="Your consulting takeaway" placeholder="Explain what this means for the client problem, and keep uncertainty explicit." rows={3} value={reviewTakeaway} onChange={(event) => setReviewTakeaway(event.target.value)} />
        </Stack>}
      </Modal>
    </Grid>
  </ObjectiveTourProvider>
  )
}
