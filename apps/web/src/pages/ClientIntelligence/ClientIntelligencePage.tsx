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
  InlineNotification,
} from '@carbon/react'
import {
  Add, ArrowRight, Locked, Link as LinkIcon, Search,
  ChartLine, Devices, UserMultiple, Document,
  CheckmarkFilled, CircleDash, ChevronLeft, ChevronRight,
} from '@carbon/icons-react'
import { useForm, Controller } from 'react-hook-form'
import { useAnalyzeUserContext, useGenerateResearchIntelligence, useResearch, useResearchGateStatus, useCompleteResearch, useSaveResearch } from '@/api/hooks/useLeads'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { ConfidenceLevel, EvidenceType, ResearchArtifact, ResearchEvidence } from '@/api/types'
import styles from './ClientIntelligencePage.module.scss'
import { PHASE_LABEL } from '@/lifecycle/phases'
import { TourProvider, type StepType } from '@reactour/tour'
import ObjectiveGuide from '@/components/shared/ObjectiveGuide'

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
      <div className={styles.evidenceCardHeader}>
        <span className={styles.evidenceCode}>{evidenceCode(item.sequenceNo)}</span>
        <Tag type={CONFIDENCE_TAG_TYPE[item.confidence]} size="sm">{item.confidence}</Tag>
      </div>
      <p className={styles.evidenceTitle}>{item.sourceTitle || item.evidenceType.replace(/_/g, ' ')}</p>
      <p className={styles.evidenceNote}>{item.note}</p>
      <div className={styles.evidenceCardFooter}>
        <Tag type="blue" size="sm">{item.evidenceType.replace(/_/g, ' ')}</Tag>
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
      <div className={styles.evidenceCardHeader}>
        <Tag type={artifact.origin === 'USER_SUPPLIED' ? 'warm-gray' : 'cyan'} size="sm">
          {artifact.origin.replace(/_/g, ' ')}
        </Tag>
        <Tag type={CONFIDENCE_TAG_TYPE[artifact.confidence]} size="sm">{artifact.confidence}</Tag>
      </div>
      <p className={styles.evidenceTitle}>{artifact.title}</p>
      <p className={styles.evidenceNote}>{artifact.summary}</p>
      <div className={styles.artifactCardFooter}>
        <Tag type={artifact.relevanceScore >= 70 ? 'green' : artifact.relevanceScore >= 45 ? 'warm-gray' : 'red'} size="sm">
          {artifact.relevanceScore}% relevant
        </Tag>
        <Button size="sm" kind="ghost" renderIcon={Add} iconDescription="Add finding" disabled={isAdding} onClick={() => onAdd(artifact)}>
          Add
        </Button>
      </div>
    </Tile>
  )
}

/** Requirement row for {@link ResearchGateChecklist} — met (✓ blue) or unmet (○ gray). */
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
          className="objective-stakeholder"
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
          <Select id="hypothesis-confidence" labelText="How confident are you?" {...register('confidence')}>{CONFIDENCE_LEVELS.map((confidence) => <SelectItem key={confidence} value={confidence} text={confidence} />)}</Select>
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
  const generateResearch = useGenerateResearchIntelligence(engagementId!)
  const analyzeUserContext = useAnalyzeUserContext(engagementId!)
  const { data: gate } = useResearchGateStatus(engagementId!)
  const [activeAction, setActiveAction] = useState<Exclude<EvidenceType, 'HYPOTHESIS'> | null>(null)
  const [researchResults, setResearchResults] = useState<ResearchArtifact[]>([])
  const [evidencePage, setEvidencePage] = useState(0)
  const [findingsPage, setFindingsPage] = useState(0)
  const [manualEvidenceOpen, setManualEvidenceOpen] = useState(false)

  const { register, handleSubmit, reset, setValue, formState: { errors } } = useForm<FormValues>({
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
  const findingsPageSize = 3
  const visibleEvidence = nonHypothesisEvidence.slice(evidencePage * evidencePageSize, (evidencePage + 1) * evidencePageSize)
  const visibleFindings = researchResults.slice(findingsPage * findingsPageSize, (findingsPage + 1) * findingsPageSize)
  const evidencePageCount = Math.max(1, Math.ceil(nonHypothesisEvidence.length / evidencePageSize))
  const findingsPageCount = Math.max(1, Math.ceil(researchResults.length / findingsPageSize))
  const readinessCompleteCount = [
    gate ? gate.evidenceCount >= gate.requiredEvidenceCount : false,
    gate?.hasStakeholderEvidence ?? false,
    gate ? gate.coverageCount >= gate.requiredCoverageCount : false,
    gate?.groundedHypothesis ?? false,
    gate ? gate.confidencePercent >= gate.requiredConfidencePercent : false,
  ].filter(Boolean).length

  const selectResearchAction = (type: Exclude<EvidenceType, 'HYPOTHESIS'>) => {
    setActiveAction(type)
    setValue('evidenceType', type)
    setResearchResults([])
    setFindingsPage(0)
    generateResearch.reset()
    analyzeUserContext.reset()
  }

  const generateSelectedResearch = () => {
    if (!activeAction) return
    generateResearch.mutate(activeAction, { onSuccess: (results) => { setResearchResults(results); setFindingsPage(0) } })
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
        relevanceScore: artifact.relevanceScore,
      },
      {
        onSuccess: () => {
          setResearchResults((items) => items.filter((item) => item.id !== artifact.id))
          setEvidencePage(0)
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
        setFindingsPage(0)
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
          setManualEvidenceOpen(false)
          setEvidencePage(0)
        },
      }
    )
  }

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  const activeResearchAction = RESEARCH_ACTIONS.find((a) => a.type === activeAction)

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
    targets: ['.objective-stakeholder', '.objective-stakeholder-research'],
  },
  {
    id: 'hypothesis',
    objective: 'Form a grounded hypothesis',
    description: 'Use the evidence you collected to explain the client’s underlying problem and likely business impact.',
    targets: ['.objective-hypothesis'],
  },
]

// Converts each objective into a Reactour step
const CLIENT_INTELLIGENCE_TOUR_STEPS: StepType[] =
  CLIENT_INTELLIGENCE_OBJECTIVES.map((objective) => ({
    selector: objective.targets[0],
    highlightedSelectors: objective.targets,
    content: (
      <div>
        <strong>{objective.objective}</strong>
        <p style={{ marginTop: '0.75rem' }}>
          {objective.description}
        </p>
      </div>
    ),
  }))

  return (
    <TourProvider
      steps={CLIENT_INTELLIGENCE_TOUR_STEPS}
      showNavigation
      showPrevNextButtons
      showDots
      showCloseButton
      scrollSmooth
      styles={{
        popover: (base) => ({
          ...base,
          borderRadius: 0,
          maxWidth: 360,
        }),
        maskArea: (base) => ({
          ...base,
          rx: 4,
        }),
      }}
    >
    <ObjectiveGuide />

    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4}>
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
              <button key={type} type="button" className={`${styles.actionButton} ${type === 'STAKEHOLDER_PROFILE' ? 'objective-stakeholder-research' : ''} ${activeAction === type ? styles.actionButtonActive : ''}`} disabled={generateResearch.isPending || analyzeUserContext.isPending} onClick={() => selectResearchAction(type)}>
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
            {activeResearchAction ? (
              <div className={styles.researchMethods}>
                <Tile className={styles.researchMethod}><h3>AI-generated scenario intelligence</h3><p>Generate controlled, scenario-aligned sources from approved facts.</p><div className={styles.methodTags}><Tag type="cyan" size="sm">Scenario-aligned</Tag><Tag type="blue" size="sm">Evidence-ready</Tag></div><Button size="sm" onClick={generateSelectedResearch} disabled={generateResearch.isPending || analyzeUserContext.isPending}>{generateResearch.isPending ? 'Generating...' : `Generate ${activeResearchAction.label}`}</Button></Tile>
                <form onSubmit={handleExternalContextSubmit(onExternalContextSubmit)}><Tile className={styles.researchMethod}><h3>Add external context</h3><p>AI correlates your input without changing canonical scenario truth.</p><TextArea id="external-context" labelText="" hideLabel placeholder="Paste a note, link or excerpt" rows={2} invalid={Boolean(externalContextErrors.context)} invalidText="Required" {...registerExternalContext('context', { required: true })} /><Button type="submit" size="sm" kind="tertiary" disabled={analyzeUserContext.isPending || generateResearch.isPending}>{analyzeUserContext.isPending ? 'Analysing...' : 'Add context'}</Button></Tile></form>
              </div>
            ) : <div className={styles.workspaceEmpty}><Search size={24} /><span>Select a research area to begin a controlled investigation.</span></div>}
            {generateResearch.isPending && <div className={styles.researchLoading}><div className={styles.researchLoadingPulse} /><span>Preparing scenario-safe intelligence...</span></div>}
            {generateResearch.isError && <InlineNotification kind="error" lowContrast title="Research could not be generated" subtitle="Retry this research action." hideCloseButton className={styles.researchError} />}
            {visibleFindings.length > 0 && <div className={styles.findingsSection}><div className={styles.compactSectionHeader}><h3>AI-generated findings</h3>{researchResults.length > findingsPageSize && <div className={styles.pager}><Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronLeft} iconDescription="Previous findings" disabled={findingsPage === 0} onClick={() => setFindingsPage((page) => page - 1)} /><span>{findingsPage + 1} / {findingsPageCount}</span><Button hasIconOnly kind="ghost" size="sm" renderIcon={ChevronRight} iconDescription="Next findings" disabled={findingsPage >= findingsPageCount - 1} onClick={() => setFindingsPage((page) => page + 1)} /></div>}</div><div className={styles.findingsGrid}>{visibleFindings.map((artifact) => <ResearchArtifactCard key={artifact.id} artifact={artifact} onAdd={addArtifactToEvidence} isAdding={saveResearch.isPending} />)}</div></div>}
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
    </Grid>
  </TourProvider>
  )
}