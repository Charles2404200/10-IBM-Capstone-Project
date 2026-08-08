import { useState } from 'react'
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
  Select,
  SelectItem,
  Tag,
  InlineNotification,
  Accordion,
  AccordionItem,
} from '@carbon/react'
import { Add } from '@carbon/icons-react'
import {
  useAddPersona,
  useAllScenariosForAdmin,
  useArchiveScenario,
  useCreateScenario,
  usePublishScenario,
  useUpdateRubricWeights,
  useUploadKnowledgeDocument,
} from '@/api/hooks/useAdminScenarios'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type {
  CreatePersonaRequest,
  CreateScenarioRequest,
  KnowledgeDocumentUploadRequest,
  ScenarioSummary,
} from '@/api/types'

function CreateScenarioForm({ onCreated }: { onCreated: (scenarioId: string) => void }) {
  const createScenario = useCreateScenario()
  const [form, setForm] = useState<CreateScenarioRequest>({ title: '', industry: '', description: '', difficulty: 3 })

  return (
    <Tile>
      <Stack gap={5}>
        <h4>Create a new scenario</h4>
        <TextInput id="new-scenario-title" labelText="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <TextInput id="new-scenario-industry" labelText="Industry" value={form.industry} onChange={(e) => setForm({ ...form, industry: e.target.value })} />
        <TextArea id="new-scenario-description" labelText="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <NumberInput
          id="new-scenario-difficulty"
          label="Difficulty (1-5)"
          value={form.difficulty}
          min={1}
          max={5}
          onChange={(_e, state) => setForm({ ...form, difficulty: Number(state?.value ?? 1) })}
        />
        {createScenario.isError && (
          <InlineNotification kind="error" title="Could not create scenario" subtitle="Please check the fields and try again." />
        )}
        <Button
          renderIcon={Add}
          disabled={!form.title.trim() || !form.industry.trim() || !form.description.trim() || createScenario.isPending}
          onClick={() =>
            createScenario.mutate(form, {
              onSuccess: (created) => {
                setForm({ title: '', industry: '', description: '', difficulty: 3 })
                onCreated(created.id)
              },
            })
          }
        >
          Create scenario (draft)
        </Button>
      </Stack>
    </Tile>
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

  return (
    <Tile>
      <Stack gap={5}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h4>{scenario.title}</h4>
            <p style={{ color: '#525252', fontSize: '0.875rem' }}>{scenario.description}</p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <Tag type="cyan">{scenario.industry}</Tag>
            <Tag type={scenario.status === 'ACTIVE' ? 'green' : scenario.status === 'DRAFT' ? 'gray' : 'red'}>
              {scenario.status}
            </Tag>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {scenario.status === 'DRAFT' && (
            <Button size="sm" onClick={() => publish.mutate(scenario.id)} disabled={publish.isPending}>
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
          <AccordionItem title={`Personas (${scenario.personas.length})`}>
            <Stack gap={4}>
              {scenario.personas.map((p) => (
                <Tile key={p.id}>
                  <strong>{p.name}</strong> — {p.jobTitle} @ {p.organisation}
                </Tile>
              ))}
              <AddPersonaForm scenarioId={scenario.id} />
            </Stack>
          </AccordionItem>
          <AccordionItem title="Rubric weights">
            <RubricWeightsForm scenario={scenario} />
          </AccordionItem>
          <AccordionItem title="Knowledge documents (RAG)">
            <KnowledgeDocumentForm scenario={scenario} />
          </AccordionItem>
        </Accordion>
      </Stack>
    </Tile>
  )
}

export default function ScenarioBuilderPage() {
  const { data: scenarios, isLoading, isError } = useAllScenariosForAdmin()
  const [showCreate, setShowCreate] = useState(false)

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={6}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Heading>Scenario Builder</Heading>
              <p style={{ color: '#525252', marginTop: '0.5rem' }}>
                Fully customisable consulting scenarios — personas, rubric weights and
                knowledge documents, no code changes required.
              </p>
            </div>
            {!showCreate && (
              <Button renderIcon={Add} onClick={() => setShowCreate(true)}>
                New scenario
              </Button>
            )}
          </div>

          {showCreate && <CreateScenarioForm onCreated={() => setShowCreate(false)} />}

          {(scenarios ?? []).length === 0 ? (
            <Tile>
              <p style={{ color: '#525252' }}>No scenarios yet. Create the first one above.</p>
            </Tile>
          ) : (
            <Stack gap={5}>
              {(scenarios ?? []).map((s) => (
                <ScenarioCard key={s.id} scenario={s} />
              ))}
            </Stack>
          )}
        </Stack>
      </Column>
    </Grid>
  )
}
