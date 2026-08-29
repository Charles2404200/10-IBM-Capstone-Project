import { useDeferredValue, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Tile,
  Button,
  TextInput,
  TextArea,
  NumberInput,
  Checkbox,
  Select,
  SelectItem,
  Tag,
  InlineNotification,
  Accordion,
  AccordionItem,
  Modal,
  Pagination,
} from '@carbon/react'
import { Add, ArrowLeft, ArrowRight } from '@carbon/icons-react'
import styles from './ScenarioBuilderPage.module.css'
import {
  useAddPersona,
  useAdminScenarioCatalog,
  useArchiveScenario,
  useCreateScenario,
  usePublishScenario,
  useUpdateRubricWeights,
  useUpdateGameplayDifficulty,
  useUploadKnowledgeDocument,
  useScenarioAuthoring,
  useGetKnowledgeDocuments,
} from '@/api/hooks/useAdminScenarios'
import ScenarioBlueprintWorkspace from '@/components/admin/ScenarioBlueprintWorkspace'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type {
  CreatePersonaRequest,
  CreateScenarioRequest,
  KnowledgeDocumentUploadRequest,
  GameplayDifficultyProfile,
  ScenarioSummary,
  KnowledgeDocumentSummary,
} from '@/api/types'

function defaultGameplayProfile(difficulty: number): GameplayDifficultyProfile {
  if (difficulty <= 2) return { level: 'EASY', researchArtifactsPerAction: 4, distractorArtifactsPerAction: 1, contradictionCount: 0, initialTrust: 40, initialInterest: 40, initialPatience: 40, meetingTurnLimit: 14, budgetVisible: true, timelinePressureDays: 30, requiredEvidenceCount: 2, requiredConfidencePercent: 40, outreachAcceptanceThreshold: 65, proposalEvidenceCoverageThreshold: 50, personaResistance: 20, scoringTolerance: 115 }
  if (difficulty >= 4) return { level: 'HARD', researchArtifactsPerAction: 6, distractorArtifactsPerAction: 3, contradictionCount: 2, initialTrust: 40, initialInterest: 40, initialPatience: 40, meetingTurnLimit: 12, budgetVisible: false, timelinePressureDays: 14, requiredEvidenceCount: 4, requiredConfidencePercent: 80, outreachAcceptanceThreshold: 82, proposalEvidenceCoverageThreshold: 75, personaResistance: 65, scoringTolerance: 85 }
  return { level: 'MEDIUM', researchArtifactsPerAction: 5, distractorArtifactsPerAction: 2, contradictionCount: 1, initialTrust: 40, initialInterest: 40, initialPatience: 40, meetingTurnLimit: 14, budgetVisible: false, timelinePressureDays: 18, requiredEvidenceCount: 3, requiredConfidencePercent: 60, outreachAcceptanceThreshold: 75, proposalEvidenceCoverageThreshold: 65, personaResistance: 50, scoringTolerance: 100 }
}

const KNOWLEDGE_COLLECTION_LABELS: Record<string, string> = {
  SCENARIO_TRUTH: 'Scenario truth',
  CONSULTING_PRACTICE: 'Consulting practice',
  ASSESSMENT_RUBRIC: 'Assessment rubric',
}

function CreateScenarioModal({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (scenarioId: string) => void
}) {
  const createScenario = useCreateScenario()
  const [form, setForm] = useState<CreateScenarioRequest>({ title: '', industry: '', description: '', difficulty: 3 })
  const canCreate = Boolean(form.title.trim() && form.industry.trim() && form.description.trim())

  const submit = () => {
    if (!canCreate || createScenario.isPending) return
    createScenario.mutate(form, {
      onSuccess: (created) => {
        setForm({ title: '', industry: '', description: '', difficulty: 3 })
        onCreated(created.id)
      },
    })
  }

  return (
    <Modal
      open={open}
      modalHeading="Create a new scenario"
      primaryButtonText={createScenario.isPending ? 'Creating...' : 'Create draft'}
      secondaryButtonText="Cancel"
      primaryButtonDisabled={!canCreate || createScenario.isPending}
      onRequestClose={onClose}
      onRequestSubmit={submit}
      className={styles.createModal}
    >
      <Stack gap={5} className={styles.createForm}>
        <p className={styles.modalIntro}>Start with the client situation. You can safely complete the blueprint, personas and rules before publishing.</p>
        <TextInput id="new-scenario-title" labelText="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <TextInput id="new-scenario-industry" labelText="Industry" value={form.industry} onChange={(e) => setForm({ ...form, industry: e.target.value })} />
        <TextArea id="new-scenario-description" labelText="Client situation" rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <Select id="new-scenario-difficulty" labelText="Difficulty" value={String(form.difficulty)} onChange={(event) => setForm({ ...form, difficulty: Number(event.target.value) })}>
          <SelectItem value="1" text="1 - Guided" />
          <SelectItem value="2" text="2 - Foundational" />
          <SelectItem value="3" text="3 - Standard" />
          <SelectItem value="4" text="4 - Advanced" />
          <SelectItem value="5" text="5 - Expert" />
        </Select>
        {createScenario.isError && (
          <InlineNotification kind="error" title="Could not create scenario" subtitle="Please check the fields and try again." />
        )}
      </Stack>
    </Modal>
  )
}

function AddPersonaForm({ scenarioId }: { scenarioId: string }) {
  const addPersona = useAddPersona(scenarioId)
  const [form, setForm] = useState<CreatePersonaRequest>({
    name: '',
    jobTitle: '',
    organisation: '',
    communicationStyle: '',
    visibleConcerns: '',
    hiddenConcerns: '',
    businessGoals: '',
  })

  return (
    <Stack gap={4}>
      <Grid narrow>
        <Column lg={8} md={4} sm={4}>
          <TextInput id={`${scenarioId}-persona-name`} labelText="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </Column>
        <Column lg={8} md={4} sm={4}>
          <TextInput id={`${scenarioId}-persona-title`} labelText="Job title" value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} />
        </Column>
        <Column lg={8} md={4} sm={4}>
          <TextInput id={`${scenarioId}-persona-org`} labelText="Organisation" value={form.organisation} onChange={(e) => setForm({ ...form, organisation: e.target.value })} />
        </Column>
        <Column lg={8} md={4} sm={4}>
          <TextInput id={`${scenarioId}-persona-style`} labelText="Communication style" value={form.communicationStyle} onChange={(e) => setForm({ ...form, communicationStyle: e.target.value })} />
        </Column>
        <Column lg={16} md={8} sm={4}>
          <TextArea id={`${scenarioId}-persona-visible`} labelText="Visible concerns" value={form.visibleConcerns} onChange={(e) => setForm({ ...form, visibleConcerns: e.target.value })} />
        </Column>
        <Column lg={16} md={8} sm={4}>
          <TextArea id={`${scenarioId}-persona-hidden`} labelText="Hidden concerns (never shown to learner)" value={form.hiddenConcerns} onChange={(e) => setForm({ ...form, hiddenConcerns: e.target.value })} />
        </Column>
        <Column lg={16} md={8} sm={4}>
          <TextArea id={`${scenarioId}-persona-goals`} labelText="Business goals" value={form.businessGoals} onChange={(e) => setForm({ ...form, businessGoals: e.target.value })} />
        </Column>
      </Grid>
      {addPersona.isError && (
        <InlineNotification kind="error" title="Could not add persona" subtitle="Please check the fields and try again." />
      )}
      <Button
        size="sm"
        renderIcon={Add}
        disabled={!form.name.trim() || !form.jobTitle.trim() || !form.organisation.trim() || addPersona.isPending}
        onClick={() =>
          addPersona.mutate(form, {
            onSuccess: () =>
              setForm({
                name: '',
                jobTitle: '',
                organisation: '',
                communicationStyle: '',
                visibleConcerns: '',
                hiddenConcerns: '',
                businessGoals: '',
              }),
          })
        }
      >
        Add persona
      </Button>
    </Stack>
  )
}

function RubricWeightsForm({ scenario }: { scenario: ScenarioSummary }) {
  const updateWeights = useUpdateRubricWeights(scenario.id)
  const [weights, setWeights] = useState<Record<string, number>>(
    Object.keys(scenario.rubricWeights ?? {}).length > 0
      ? scenario.rubricWeights
      : { 'Problem Structuring': 25, Communication: 25, 'Stakeholder Management': 25, Rigor: 25 },
  )
  const total = Object.values(weights).reduce((sum, w) => sum + w, 0)

  return (
    <Stack gap={4}>
      <Grid narrow>
        {Object.entries(weights).map(([name, weight]) => (
          <Column key={name} lg={4} md={4} sm={4}>
            <NumberInput
              id={`${scenario.id}-weight-${name}`}
              label={name}
              value={weight}
              min={0}
              max={100}
              onChange={(_e, state) => setWeights({ ...weights, [name]: Number(state?.value ?? 0) })}
            />
          </Column>
        ))}
      </Grid>
      <Tag type={total === 100 ? 'green' : 'red'}>Total: {total}% (must equal 100%)</Tag>
      {updateWeights.isError && (
        <InlineNotification kind="error" title="Could not save rubric weights" subtitle="Weights must sum to exactly 100." />
      )}
      <Button size="sm" disabled={total !== 100 || updateWeights.isPending} onClick={() => updateWeights.mutate(weights)}>
        Save rubric weights
      </Button>
    </Stack>
  )
}

function GameplayDifficultyForm({ scenario }: { scenario: ScenarioSummary }) {
  const updateGameplay = useUpdateGameplayDifficulty(scenario.id)
  const [profile, setProfile] = useState<GameplayDifficultyProfile>(
    scenario.gameplayDifficulty ?? defaultGameplayProfile(scenario.difficulty),
  )
  const updateNumber = (field: Exclude<keyof GameplayDifficultyProfile, 'level' | 'budgetVisible'>, value: number) =>
    setProfile((current) => ({ ...current, [field]: value }))

  return (
    <Stack gap={5}>
      <p style={{ color: '#525252', fontSize: '0.875rem' }}>
        These rules apply to new engagements only. In-progress learners continue with the immutable profile captured when they started.
      </p>
      <Grid narrow>
        <Column lg={4} md={4} sm={4}>
          <Select id={`${scenario.id}-difficulty-level`} labelText="Gameplay tier" value={profile.level}
            onChange={(event) => setProfile((current) => ({ ...current, level: event.target.value as GameplayDifficultyProfile['level'] }))}>
            <SelectItem value="EASY" text="Easy" />
            <SelectItem value="MEDIUM" text="Medium" />
            <SelectItem value="HARD" text="Hard" />
          </Select>
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-research-artifacts`} label="Research artefacts per action" value={profile.researchArtifactsPerAction} min={2} max={8}
            onChange={(_event, state) => updateNumber('researchArtifactsPerAction', Number(state?.value ?? 2))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-research-distractors`} label="Distractor artefacts" value={profile.distractorArtifactsPerAction} min={0} max={7}
            onChange={(_event, state) => updateNumber('distractorArtifactsPerAction', Number(state?.value ?? 0))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-contradictions`} label="Conflicting signals" value={profile.contradictionCount} min={0} max={6}
            onChange={(_event, state) => updateNumber('contradictionCount', Number(state?.value ?? 0))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-turn-limit`} label="Meeting learner turns" value={profile.meetingTurnLimit} min={4} max={20}
            onChange={(_event, state) => updateNumber('meetingTurnLimit', Number(state?.value ?? 4))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-persona-resistance`} label="Persona resistance" value={profile.personaResistance} min={0} max={100}
            onChange={(_event, state) => updateNumber('personaResistance', Number(state?.value ?? 0))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-research-confidence`} label="Research confidence gate" value={profile.requiredConfidencePercent} min={20} max={90}
            onChange={(_event, state) => updateNumber('requiredConfidencePercent', Number(state?.value ?? 20))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-outreach-gate`} label="Outreach acceptance gate" value={profile.outreachAcceptanceThreshold} min={50} max={95}
            onChange={(_event, state) => updateNumber('outreachAcceptanceThreshold', Number(state?.value ?? 50))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-proposal-coverage`} label="Proposal evidence coverage" value={profile.proposalEvidenceCoverageThreshold} min={30} max={95}
            onChange={(_event, state) => updateNumber('proposalEvidenceCoverageThreshold', Number(state?.value ?? 30))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-pressure-days`} label="Timeline pressure (days)" value={profile.timelinePressureDays} min={1} max={90}
            onChange={(_event, state) => updateNumber('timelinePressureDays', Number(state?.value ?? 1))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-required-evidence`} label="Evidence items required" value={profile.requiredEvidenceCount} min={2} max={8}
            onChange={(_event, state) => updateNumber('requiredEvidenceCount', Number(state?.value ?? 2))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-initial-trust`} label="Initial trust" value={profile.initialTrust} min={0} max={40}
            onChange={(_event, state) => updateNumber('initialTrust', Number(state?.value ?? 0))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-initial-interest`} label="Initial interest" value={profile.initialInterest} min={0} max={40}
            onChange={(_event, state) => updateNumber('initialInterest', Number(state?.value ?? 0))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-initial-patience`} label="Initial patience" value={profile.initialPatience} min={0} max={40}
            onChange={(_event, state) => updateNumber('initialPatience', Number(state?.value ?? 0))} />
        </Column>
        <Column lg={4} md={4} sm={4}>
          <NumberInput id={`${scenario.id}-scoring-tolerance`} label="Scoring tolerance" value={profile.scoringTolerance} min={70} max={130}
            onChange={(_event, state) => updateNumber('scoringTolerance', Number(state?.value ?? 70))} />
        </Column>
        <Column lg={16} md={8} sm={4}>
          <Checkbox id={`${scenario.id}-budget-visible`} labelText="Show the budget signal during research" checked={profile.budgetVisible}
            onChange={(_event, state) => setProfile((current) => ({ ...current, budgetVisible: Boolean(state.checked) }))} />
        </Column>
      </Grid>
      {updateGameplay.isError && <InlineNotification kind="error" title="Could not save gameplay rules" subtitle="No engagement was changed. Review the values and try again." />}
      <Button size="sm" disabled={updateGameplay.isPending} onClick={() => updateGameplay.mutate(profile)}>
        Save gameplay rules
      </Button>
    </Stack>
  )
}

function KnowledgeDocumentForm({ scenario }: { scenario: ScenarioSummary }) {
  const uploadDocument = useUploadKnowledgeDocument(scenario.id)
  const [form, setForm] = useState<KnowledgeDocumentUploadRequest>({
    personaId: null,
    collection: 'SCENARIO_TRUTH',
    title: '',
    content: '',
  })

  return (
    <Stack gap={4}>
      <Grid narrow>
        <Column lg={8} md={4} sm={4}>
          <Select
            id={`${scenario.id}-doc-collection`}
            labelText="Collection"
            value={form.collection}
            onChange={(e) => setForm({ ...form, collection: e.target.value as KnowledgeDocumentUploadRequest['collection'] })}
          >
            <SelectItem value="SCENARIO_TRUTH" text="Scenario truth (ground facts)" />
            <SelectItem value="CONSULTING_PRACTICE" text="Consulting practice guidance" />
            <SelectItem value="ASSESSMENT_RUBRIC" text="Assessment rubric reference" />
          </Select>
        </Column>
        <Column lg={8} md={4} sm={4}>
          <Select
            id={`${scenario.id}-doc-persona`}
            labelText="Persona scope (optional)"
            value={form.personaId ?? ''}
            onChange={(e) => setForm({ ...form, personaId: e.target.value || null })}
          >
            <SelectItem value="" text="Scenario-wide (no specific persona)" />
            {scenario.personas.map((p) => (
              <SelectItem key={p.id} value={p.id} text={p.name} />
            ))}
          </Select>
        </Column>
        <Column lg={16} md={8} sm={4}>
          <TextInput id={`${scenario.id}-doc-title`} labelText="Document title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </Column>
        <Column lg={16} md={8} sm={4}>
          <TextArea id={`${scenario.id}-doc-content`} labelText="Content" rows={5} value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
        </Column>
      </Grid>
      {uploadDocument.isError && (
        <InlineNotification kind="error" title="Could not upload document" subtitle="Please check the fields and try again." />
      )}
      {uploadDocument.isSuccess && (
        <InlineNotification kind="success" title="Document ingested" subtitle="It is now available to the RAG retrieval pipeline." />
      )}
      <Button
        size="sm"
        renderIcon={Add}
        disabled={!form.title.trim() || !form.content.trim() || uploadDocument.isPending}
        onClick={() => uploadDocument.mutate(form, { onSuccess: () => setForm({ ...form, title: '', content: '' }) })}
      >
        Upload document
      </Button>
    </Stack>
  )
}

function ScenarioCard({ scenario }: { scenario: ScenarioSummary }) {
  const publish = usePublishScenario()
  const archive = useArchiveScenario()
  const authoring = useScenarioAuthoring(scenario.id)
  const readyToPublish = authoring.data?.readiness.readyToPublish ?? false

  return (
    <Tile>
      <Stack gap={5}>
        <div className={styles.scenarioHeader}>
          <div>
            <h4>{scenario.title}</h4>
            <p>{scenario.description}</p>
          </div>
          <div className={styles.tags}>
            <Tag type="cyan">{scenario.industry}</Tag>
            <Tag type={scenario.status === 'ACTIVE' ? 'green' : scenario.status === 'DRAFT' ? 'gray' : 'red'}>
              {scenario.status}
            </Tag>
          </div>
        </div>

        <div className={styles.lifecycleActions}>
          {scenario.status === 'DRAFT' && (
            <Button size="sm" onClick={() => publish.mutate(scenario.id)} disabled={publish.isPending || !readyToPublish} title={readyToPublish ? 'Publish this scenario revision' : 'Complete the publishing checklist first'}>
              Publish
            </Button>
          )}
          {scenario.status === 'ACTIVE' && (
            <Button size="sm" kind="danger--tertiary" onClick={() => archive.mutate(scenario.id)} disabled={archive.isPending}>
              Archive
            </Button>
          )}
        </div>

        <Accordion>
          <AccordionItem title="Authoring blueprint">
            <ScenarioBlueprintWorkspace scenario={scenario} />
          </AccordionItem>
          <AccordionItem title={`Personas (${scenario.personas.length})`}>
            <Stack gap={4}>
              {scenario.personas.map((p) => (
                <div className={styles.personaRow} key={p.id}>
                  <strong>{p.name}</strong> — {p.jobTitle} @ {p.organisation}
                </div>
              ))}
              {scenario.status === 'DRAFT' ? (
                <AddPersonaForm scenarioId={scenario.id} />
              ) : (
                <p>Personas are locked in this published revision. Create a revision to make changes.</p>
              )}
            </Stack>
          </AccordionItem>
          <AccordionItem title="Rubric weights">
            {scenario.status === 'DRAFT' ? (
              <RubricWeightsForm scenario={scenario} />
            ) : (
              <p>Rubric weights are locked in this published revision. Create a revision to make changes.</p>
            )}
          </AccordionItem>
          <AccordionItem title="Gameplay difficulty">
            {scenario.status === 'DRAFT' ? (
              <GameplayDifficultyForm scenario={scenario} />
            ) : (
              <p>Difficulty rules are locked in this published revision. Create a revision to make changes.</p>
            )}
          </AccordionItem>
          <AccordionItem title="Knowledge documents (RAG)">
            <Stack gap={5}>
              {scenario.status === 'DRAFT' ? (
                <KnowledgeDocumentForm scenario={scenario} />
              ) : (
                <p>Knowledge documents are locked in this published revision. Create a revision to make changes.</p>
              )}
              <KnowledgeDocumentList scenario={scenario} />
            </Stack>
          </AccordionItem>
        </Accordion>
      </Stack>
    </Tile>
  )
}

function ScenarioLibraryRow({ scenario, onOpen }: { scenario: ScenarioSummary; onOpen: (id: string) => void }) {
  const difficultyLabel = ['Guided', 'Foundational', 'Standard', 'Advanced', 'Expert'][Math.max(0, scenario.difficulty - 1)]

  return (
    <Tile className={styles.libraryRow}>
      <div className={styles.libraryIdentity}>
        <div className={styles.scenarioMark}>{scenario.title.slice(0, 1).toUpperCase()}</div>
        <div>
          <div className={styles.libraryTitleLine}>
            <h3>{scenario.title}</h3>
            <Tag type="cyan">{scenario.industry}</Tag>
            <Tag type={scenario.status === 'ACTIVE' ? 'green' : scenario.status === 'DRAFT' ? 'gray' : 'red'}>{scenario.status}</Tag>
          </div>
          <p>{scenario.description}</p>
        </div>
      </div>
      <div className={styles.libraryMetadata}>
        <span><strong>v{scenario.version}</strong> version</span>
        <span><strong>{scenario.personas.length}</strong> personas</span>
        <span><strong>{difficultyLabel}</strong> difficulty</span>
      </div>
      <Button kind="ghost" size="sm" renderIcon={ArrowRight} iconDescription="Open scenario editor" onClick={() => onOpen(scenario.id)}>
        Open editor
      </Button>
    </Tile>
  )
}

function ScenarioEditor({ scenarioId, onBack }: { scenarioId: string; onBack: () => void }) {
  const authoring = useScenarioAuthoring(scenarioId)

  if (authoring.isLoading) return <LoadingState />
  if (authoring.isError || !authoring.data) return <ErrorState />

  return (
    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={5}>
          <div className={styles.editorBackRow}>
            <Button kind="ghost" size="sm" renderIcon={ArrowLeft} onClick={onBack}>All scenarios</Button>
            <span>Scenario editor</span>
          </div>
          <ScenarioCard scenario={authoring.data.scenario} />
        </Stack>
      </Column>
    </Grid>
  )
}

function KnowledgeDocumentList({ scenario }: { scenario: ScenarioSummary }) {
  const documents = useGetKnowledgeDocuments(scenario.id)

  if (documents.isLoading) {
    return <LoadingState description="Loading ingested documents…" />
  }
  if (documents.isError) {
    return <ErrorState title="Could not load knowledge documents" />
  }
  if (!documents.data || documents.data.length === 0) {
    return <p>No documents ingested yet for this scenario.</p>
  }

  const personaName = (personaId: string | null) =>
    personaId ? scenario.personas.find((p) => p.id === personaId)?.name ?? 'Unknown persona' : 'Scenario-wide'

  return (
    <Stack gap={3}>
      <p style={{ margin: 0, fontSize: '.8125rem', color: '#525252' }}>
        {documents.data.length} document{documents.data.length === 1 ? '' : 's'} ingested
      </p>
      {documents.data.map((doc) => (
        <KnowledgeDocumentRow key={doc.id} doc={doc} personaName={personaName(doc.personaId)} />
      ))}
    </Stack>
  )
}

const PREVIEW_LENGTH = 200

function KnowledgeDocumentRow({
  doc,
  personaName,
}: {
  doc: KnowledgeDocumentSummary
  personaName: string
}) {
  const [expanded, setExpanded] = useState(false)
  const isLong = doc.sourceText.length > PREVIEW_LENGTH
  const preview = isLong ? doc.sourceText.slice(0, PREVIEW_LENGTH).trimEnd() + '…' : doc.sourceText

  return (
    <div className={styles.personaRow}>
      <div className={styles.scenarioHeader}>
        <div>
          <strong>{doc.title}</strong>
          <p style={{ margin: '.25rem 0 0', fontSize: '.8125rem', color: '#525252' }}>
            {personaName} · Ingested {new Date(doc.createdAt).toLocaleDateString()}
          </p>
        </div>
        <Tag type="blue">{KNOWLEDGE_COLLECTION_LABELS[doc.collection] ?? doc.collection}</Tag>
      </div>
      <p style={{ margin: '.5rem 0 0', fontSize: '.8125rem', whiteSpace: 'pre-wrap' }}>
        {expanded ? doc.sourceText : preview}
      </p>
      {isLong && (
        <Button kind="ghost" size="sm" onClick={() => setExpanded((v) => !v)} style={{ paddingLeft: 0 }}>
          {expanded ? 'Show less' : 'Show more'}
        </Button>
      )}
    </div>
  )
}

export default function ScenarioBuilderPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedScenarioId = searchParams.get('scenario')
  const [showCreate, setShowCreate] = useState(false)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(1)
  const deferredQuery = useDeferredValue(query)
  const catalogueFilters = useMemo(() => ({
    search: deferredQuery.trim() || undefined,
    status: status === 'ALL' ? undefined : status as 'DRAFT' | 'ACTIVE' | 'ARCHIVED',
    page: page - 1,
    size: 12,
  }), [deferredQuery, page, status])
  const catalogue = useAdminScenarioCatalog(catalogueFilters, !selectedScenarioId)

  const openScenario = (scenarioId: string) => setSearchParams({ scenario: scenarioId })
  const returnToLibrary = () => setSearchParams({})
  const updateSearch = (value: string) => {
    setQuery(value)
    setPage(1)
  }
  const updateStatus = (value: string) => {
    setStatus(value)
    setPage(1)
  }

  if (selectedScenarioId) return <ScenarioEditor scenarioId={selectedScenarioId} onBack={returnToLibrary} />
  if (catalogue.isLoading) return <LoadingState />
  if (catalogue.isError) return <ErrorState />

  const scenarioPage = catalogue.data
  const scenarios = scenarioPage?.items ?? []

  return (
    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={6}>
          <div className={styles.pageHeader}>
            <div>
              <p className={styles.eyebrow}>Simulation authoring</p>
              <Heading>Scenario management</Heading>
              <p className={styles.description}>
                Fully customisable consulting scenarios — personas, rubric weights and
                knowledge documents, no code changes required.
              </p>
            </div>
            <Button renderIcon={Add} onClick={() => setShowCreate(true)}>New scenario</Button>
          </div>

          <section className={styles.libraryGuide}>
            <div><span>1</span><strong>Create draft</strong><p>Set the client situation and difficulty.</p></div>
            <div><span>2</span><strong>Design experience</strong><p>Add truth, personas, leads and learning rules.</p></div>
            <div><span>3</span><strong>Publish safely</strong><p>Only completed drafts become learner-visible.</p></div>
          </section>

          <section className={styles.catalogToolbar}>
            <TextInput id="scenario-search" labelText="Search scenario library" placeholder="Title, industry or client situation" value={query} onChange={(event) => updateSearch(event.target.value)} />
            <Select id="scenario-status-filter" labelText="Lifecycle status" value={status} onChange={(event) => updateStatus(event.target.value)}>
              <SelectItem value="ALL" text="All statuses" />
              <SelectItem value="DRAFT" text="Draft" />
              <SelectItem value="ACTIVE" text="Active" />
              <SelectItem value="ARCHIVED" text="Archived" />
            </Select>
            <div className={styles.catalogCount}><strong>{scenarioPage?.totalElements ?? 0}</strong><span>scenarios</span></div>
          </section>

          {scenarios.length === 0 ? (
            <Tile className={styles.emptyState}>
              <h3>No scenarios found</h3>
              <p>Adjust the library filters or create a scenario draft.</p>
            </Tile>
          ) : (
            <section className={styles.libraryList} aria-busy={catalogue.isFetching}>
              {scenarios.map((scenario) => <ScenarioLibraryRow key={scenario.id} scenario={scenario} onOpen={openScenario} />)}
            </section>
          )}

          {(scenarioPage?.totalElements ?? 0) > 0 && (
            <Pagination
              page={page}
              pageSize={scenarioPage?.size ?? 12}
              pageSizes={[12]}
              totalItems={scenarioPage?.totalElements ?? 0}
              onChange={({ page: nextPage }) => setPage(nextPage)}
            />
          )}
        </Stack>
      </Column>
      <CreateScenarioModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(scenarioId) => {
          setShowCreate(false)
          openScenario(scenarioId)
        }}
      />
    </Grid>
  )
}
