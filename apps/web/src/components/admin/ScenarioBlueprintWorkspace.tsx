import { useEffect, useState } from 'react'
import {
  Button,
  Checkbox,
  InlineLoading,
  InlineNotification,
  NumberInput,
  Select,
  SelectItem,
  Stack,
  Tag,
  TextArea,
  TextInput,
  Tile,
} from '@carbon/react'
import { Add, CopyFile, Edit, TrashCan } from '@carbon/icons-react'
import {
  useCreateScenarioLead,
  useCreateScenarioRevision,
  useDeleteScenarioLead,
  useScenarioAuthoring,
  useScenarioAuthoringLeads,
  useUpdateScenarioAuthoringConfig,
  useUpdateScenarioBlueprint,
  useUpdateScenarioLead,
} from '@/api/hooks/useAdminScenarios'
import type {
  CanonicalFact,
  EvidenceType,
  LeadAuthoringRequest,
  LeadAuthoringView,
  RevealRule,
  RevealTarget,
  ScenarioAuthoringConfig,
  ScenarioSummary,
  UpdateScenarioBlueprintRequest,
} from '@/api/types'
import styles from '@/pages/Admin/ScenarioBuilderPage.module.css'

const evidenceTypes: EvidenceType[] = ['COMPANY_NEWS', 'STAKEHOLDER_PROFILE', 'FINANCIAL_SIGNAL', 'TECHNOLOGY_INDICATOR', 'MARKET_TREND']
const targets: RevealTarget[] = ['DECISION_MAKER', 'PAIN_SEVERITY', 'TECHNOLOGY_STACK', 'BUDGET_SIGNAL', 'POTENTIAL_VALUE']

const label = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())

function blueprintFrom(scenario: ScenarioSummary): UpdateScenarioBlueprintRequest {
  return {
    title: scenario.title,
    industry: scenario.industry,
    description: scenario.description,
    difficulty: scenario.difficulty,
    consultantRole: scenario.briefing.consultantRole,
    objective: scenario.briefing.objective,
    successCriteria: scenario.briefing.successCriteria,
    simulatedDays: scenario.briefing.simulatedDays,
    informationAmbiguity: scenario.difficultyProfile.informationAmbiguity,
    stakeholderComplexity: scenario.difficultyProfile.stakeholderComplexity,
    commercialPressure: scenario.difficultyProfile.commercialPressure,
  }
}

const emptyLead: LeadAuthoringRequest = {
  companyName: '', industry: '', publicDescription: '', difficulty: 'MEDIUM', potentialValueRange: '',
  decisionMaker: '', technologyStack: '', budgetSignal: '', painSeverity: '', signals: [],
}

function newFact(): CanonicalFact {
  return { id: `fact-${crypto.randomUUID().slice(0, 8)}`, label: '', value: '', evidenceType: 'COMPANY_NEWS', availableInResearch: true }
}

function newRule(): RevealRule {
  return { target: 'DECISION_MAKER', requiredEvidenceTypes: ['STAKEHOLDER_PROFILE'], minimumEvidenceCount: 1 }
}

export default function ScenarioBlueprintWorkspace({ scenario }: { scenario: ScenarioSummary }) {
  const authoring = useScenarioAuthoring(scenario.id)
  const leads = useScenarioAuthoringLeads(scenario.id)
  const updateBlueprint = useUpdateScenarioBlueprint(scenario.id)
  const updateConfig = useUpdateScenarioAuthoringConfig(scenario.id)
  const createRevision = useCreateScenarioRevision(scenario.id)
  const createLead = useCreateScenarioLead(scenario.id)
  const deleteLead = useDeleteScenarioLead(scenario.id)
  const updateLead = useUpdateScenarioLead(scenario.id)
  const [blueprint, setBlueprint] = useState(() => blueprintFrom(scenario))
  const [config, setConfig] = useState<ScenarioAuthoringConfig>({ canonicalFacts: [], revealRules: [] })
  const [lead, setLead] = useState<LeadAuthoringRequest>({ ...emptyLead })
  const [revisionCreated, setRevisionCreated] = useState(false)
  const [editingLeadId, setEditingLeadId] = useState<string | null>(null)

  useEffect(() => setBlueprint(blueprintFrom(scenario)), [scenario])
  useEffect(() => {
    if (authoring.data) setConfig(authoring.data.config)
  }, [authoring.data])

  if (authoring.isLoading || leads.isLoading) return <InlineLoading description="Loading authoring workspace" />
  if (authoring.isError || leads.isError || !authoring.data) {
    return <InlineNotification kind="error" title="Authoring workspace unavailable" subtitle="Refresh and try again. No scenario content was changed." />
  }

  const { readiness } = authoring.data
  const editable = scenario.status === 'DRAFT'
  const saveBlueprint = () => updateBlueprint.mutate(blueprint)
  const saveConfig = () => updateConfig.mutate(config)
  const startEditingLead = (item: LeadAuthoringView) => {
    setEditingLeadId(item.id)
    setLead({
      companyName: item.companyName,
      industry: item.industry,
      publicDescription: item.publicDescription ?? '',
      difficulty: item.difficulty as LeadAuthoringRequest['difficulty'],
      potentialValueRange: item.potentialValueRange ?? '',
      decisionMaker: item.decisionMaker ?? '',
      technologyStack: item.technologyStack ?? '',
      budgetSignal: item.budgetSignal ?? '',
      painSeverity: item.painSeverity ?? '',
      signals: item.signals,
    })
  }
  const resetLeadForm = () => {
    setEditingLeadId(null)
    setLead({ ...emptyLead })
  }
  const parseSignals = (value: string) => value.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
    const [signalLabel, category] = line.split('|').map((part) => part.trim())
    return { label: signalLabel, category: category || 'General' }
  })

  return (
    <Stack gap={5} className={styles.authoringWorkspace}>
      <Tile className={styles.readinessTile}>
        <div className={styles.readinessHeader}>
          <div>
            <p className={styles.sectionEyebrow}>Version {scenario.version} · {scenario.status}</p>
            <h5>{readiness.readyToPublish ? 'Ready to publish' : 'Publishing checklist'}</h5>
          </div>
          <Tag type={readiness.readyToPublish ? 'green' : 'warm-gray'}>{readiness.readyToPublish ? 'Ready' : `${readiness.blockers.length} actions needed`}</Tag>
        </div>
        {readiness.blockers.length > 0 ? (
          <ul className={styles.readinessList}>{readiness.blockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul>
        ) : <p className={styles.readyCopy}>This revision has a persona, a playable lead, governed truth, reveal rules and an assessment rubric.</p>}
        <div className={styles.readinessMetrics}>
          <span>{readiness.personaCount} personas</span><span>{readiness.leadCount} leads</span>
          <span>{readiness.canonicalFactCount} facts</span><span>{readiness.revealRuleCount} reveal rules</span>
        </div>
      </Tile>

      {!editable && (
        <Tile className={styles.revisionTile}>
          <h5>Published content is immutable</h5>
          <p>Create a draft revision to change content. Existing learner engagements remain on this published version.</p>
          {revisionCreated && <InlineNotification kind="success" title="Draft revision created" subtitle="Find the new draft in the scenario catalogue and complete its checklist before publishing." hideCloseButton />}
          <Button size="sm" renderIcon={CopyFile} disabled={createRevision.isPending} onClick={() => createRevision.mutate(undefined, { onSuccess: () => setRevisionCreated(true) })}>
            Create draft revision
          </Button>
        </Tile>
      )}

      {editable && <>
        <section className={styles.authoringSection}>
          <div><p className={styles.sectionEyebrow}>01 · Scenario blueprint</p><h5>Context and learning contract</h5></div>
          <div className={styles.authoringGrid}>
            <TextInput id={`${scenario.id}-blueprint-title`} labelText="Scenario title" value={blueprint.title} onChange={(event) => setBlueprint({ ...blueprint, title: event.target.value })} />
            <TextInput id={`${scenario.id}-blueprint-industry`} labelText="Industry" value={blueprint.industry} onChange={(event) => setBlueprint({ ...blueprint, industry: event.target.value })} />
            <TextInput id={`${scenario.id}-blueprint-role`} labelText="Learner role" value={blueprint.consultantRole} onChange={(event) => setBlueprint({ ...blueprint, consultantRole: event.target.value })} />
            <NumberInput id={`${scenario.id}-blueprint-days`} label="Simulated days" min={1} max={90} value={blueprint.simulatedDays} onChange={(_event, state) => setBlueprint({ ...blueprint, simulatedDays: Number(state?.value ?? 10) })} />
            <TextArea id={`${scenario.id}-blueprint-description`} className={styles.fullWidth} labelText="Scenario description" rows={3} value={blueprint.description} onChange={(event) => setBlueprint({ ...blueprint, description: event.target.value })} />
            <TextArea id={`${scenario.id}-blueprint-objective`} className={styles.fullWidth} labelText="Learning objective" rows={3} value={blueprint.objective} onChange={(event) => setBlueprint({ ...blueprint, objective: event.target.value })} />
            <TextArea id={`${scenario.id}-blueprint-criteria`} className={styles.fullWidth} labelText="Success criteria (one per line)" rows={4} value={blueprint.successCriteria.join('\n')} onChange={(event) => setBlueprint({ ...blueprint, successCriteria: event.target.value.split('\n').map((line) => line.trim()).filter(Boolean) })} />
          </div>
          {updateBlueprint.isError && <InlineNotification kind="error" title="Blueprint could not be saved" subtitle="Only draft versions can be edited. Review the required fields and try again." />}
          <Button size="sm" disabled={updateBlueprint.isPending || !blueprint.title || !blueprint.industry || !blueprint.description || !blueprint.objective} onClick={saveBlueprint}>Save blueprint</Button>
        </section>

        <section className={styles.authoringSection}>
          <div><p className={styles.sectionEyebrow}>02 · Canonical truth</p><h5>Facts the simulation is allowed to use</h5><p className={styles.sectionHelp}>Facts are versioned ground truth. AI may phrase them, but cannot add or replace them.</p></div>
          <Stack gap={3}>
            {config.canonicalFacts.map((fact, index) => (
              <div className={styles.factRow} key={fact.id}>
                <TextInput id={`${scenario.id}-fact-id-${index}`} labelText="Fact ID" value={fact.id} onChange={(event) => setConfig({ ...config, canonicalFacts: config.canonicalFacts.map((item, itemIndex) => itemIndex === index ? { ...item, id: event.target.value } : item) })} />
                <TextInput id={`${scenario.id}-fact-label-${index}`} labelText="Fact label" value={fact.label} onChange={(event) => setConfig({ ...config, canonicalFacts: config.canonicalFacts.map((item, itemIndex) => itemIndex === index ? { ...item, label: event.target.value } : item) })} />
                <Select id={`${scenario.id}-fact-type-${index}`} labelText="Research category" value={fact.evidenceType} onChange={(event) => setConfig({ ...config, canonicalFacts: config.canonicalFacts.map((item, itemIndex) => itemIndex === index ? { ...item, evidenceType: event.target.value as EvidenceType } : item) })}>
                  {evidenceTypes.map((type) => <SelectItem key={type} value={type} text={label(type)} />)}
                </Select>
                <TextArea id={`${scenario.id}-fact-value-${index}`} labelText="Canonical value" rows={2} value={fact.value} onChange={(event) => setConfig({ ...config, canonicalFacts: config.canonicalFacts.map((item, itemIndex) => itemIndex === index ? { ...item, value: event.target.value } : item) })} />
                <Checkbox id={`${scenario.id}-fact-research-${index}`} labelText="Available in research" checked={fact.availableInResearch} onChange={(_event, state) => setConfig({ ...config, canonicalFacts: config.canonicalFacts.map((item, itemIndex) => itemIndex === index ? { ...item, availableInResearch: Boolean(state.checked) } : item) })} />
                <Button hasIconOnly kind="ghost" renderIcon={TrashCan} iconDescription={`Remove ${fact.label || 'fact'}`} onClick={() => setConfig({ ...config, canonicalFacts: config.canonicalFacts.filter((_item, itemIndex) => itemIndex !== index) })} />
              </div>
            ))}
            <Button size="sm" kind="tertiary" renderIcon={Add} onClick={() => setConfig({ ...config, canonicalFacts: [...config.canonicalFacts, newFact()] })}>Add canonical fact</Button>
          </Stack>
        </section>

        <section className={styles.authoringSection}>
          <div><p className={styles.sectionEyebrow}>03 · Evidence reveal rules</p><h5>What learners must earn before an insight appears</h5></div>
          <Stack gap={3}>
            {config.revealRules.map((rule, index) => (
              <div className={styles.ruleRow} key={`${rule.target}-${index}`}>
                <Select id={`${scenario.id}-reveal-target-${index}`} labelText="Unlock field" value={rule.target} onChange={(event) => setConfig({ ...config, revealRules: config.revealRules.map((item, itemIndex) => itemIndex === index ? { ...item, target: event.target.value as RevealTarget } : item) })}>
                  {targets.map((target) => <SelectItem key={target} value={target} text={label(target)} />)}
                </Select>
                <NumberInput id={`${scenario.id}-reveal-count-${index}`} label="Minimum evidence" min={1} max={8} value={rule.minimumEvidenceCount} onChange={(_event, state) => setConfig({ ...config, revealRules: config.revealRules.map((item, itemIndex) => itemIndex === index ? { ...item, minimumEvidenceCount: Number(state?.value ?? 1) } : item) })} />
                <div className={styles.ruleTypes}><span>Required evidence types</span>{evidenceTypes.map((type) => <Checkbox key={type} id={`${scenario.id}-reveal-${index}-${type}`} labelText={label(type)} checked={rule.requiredEvidenceTypes.includes(type)} onChange={(_event, state) => setConfig({ ...config, revealRules: config.revealRules.map((item, itemIndex) => itemIndex === index ? { ...item, requiredEvidenceTypes: state.checked ? [...new Set([...item.requiredEvidenceTypes, type])] : item.requiredEvidenceTypes.filter((value) => value !== type) } : item) })} />)}</div>
                <Button hasIconOnly kind="ghost" renderIcon={TrashCan} iconDescription={`Remove ${label(rule.target)} rule`} onClick={() => setConfig({ ...config, revealRules: config.revealRules.filter((_item, itemIndex) => itemIndex !== index) })} />
              </div>
            ))}
            <Button size="sm" kind="tertiary" renderIcon={Add} onClick={() => setConfig({ ...config, revealRules: [...config.revealRules, newRule()] })}>Add reveal rule</Button>
            {updateConfig.isError && <InlineNotification kind="error" title="Truth configuration could not be saved" subtitle="Fact IDs and reveal targets must be unique. Each rule needs at least one evidence type." />}
            <Button size="sm" disabled={updateConfig.isPending || config.canonicalFacts.some((fact) => !fact.id.trim() || !fact.label.trim() || !fact.value.trim()) || config.revealRules.some((rule) => rule.requiredEvidenceTypes.length === 0)} onClick={saveConfig}>Save truth and reveal rules</Button>
          </Stack>
        </section>

        <section className={styles.authoringSection}>
          <div><p className={styles.sectionEyebrow}>04 · Lead definitions</p><h5>Playable entry points</h5><p className={styles.sectionHelp}>Lead intelligence is canonical scenario truth and is never returned by learner-facing lead APIs.</p></div>
          <div className={styles.leadList}>{(leads.data ?? []).map((item) => <div className={styles.leadRow} key={item.id}><div><strong>{item.companyName}</strong><span>{item.industry} · {item.difficulty}</span></div><div><Button hasIconOnly kind="ghost" renderIcon={Edit} iconDescription={`Edit ${item.companyName}`} disabled={deleteLead.isPending} onClick={() => startEditingLead(item)} /><Button hasIconOnly kind="ghost" renderIcon={TrashCan} iconDescription={`Delete ${item.companyName}`} disabled={deleteLead.isPending} onClick={() => deleteLead.mutate(item.id)} /></div></div>)}</div>
          <div className={styles.authoringGrid}>
            <TextInput id={`${scenario.id}-lead-company`} labelText="Company" value={lead.companyName} onChange={(event) => setLead({ ...lead, companyName: event.target.value })} />
            <TextInput id={`${scenario.id}-lead-industry`} labelText="Industry" value={lead.industry} onChange={(event) => setLead({ ...lead, industry: event.target.value })} />
            <Select id={`${scenario.id}-lead-difficulty`} labelText="Difficulty" value={lead.difficulty} onChange={(event) => setLead({ ...lead, difficulty: event.target.value as LeadAuthoringRequest['difficulty'] })}><SelectItem value="EASY" text="Easy" /><SelectItem value="MEDIUM" text="Medium" /><SelectItem value="HARD" text="Hard" /></Select>
            <TextInput id={`${scenario.id}-lead-decision-maker`} labelText="Decision maker" value={lead.decisionMaker} onChange={(event) => setLead({ ...lead, decisionMaker: event.target.value })} />
            <TextArea id={`${scenario.id}-lead-description`} className={styles.fullWidth} labelText="Public description" rows={2} value={lead.publicDescription} onChange={(event) => setLead({ ...lead, publicDescription: event.target.value })} />
            <TextInput id={`${scenario.id}-lead-tech`} labelText="Technology stack" value={lead.technologyStack} onChange={(event) => setLead({ ...lead, technologyStack: event.target.value })} />
            <TextInput id={`${scenario.id}-lead-budget`} labelText="Budget signal" value={lead.budgetSignal} onChange={(event) => setLead({ ...lead, budgetSignal: event.target.value })} />
            <TextInput id={`${scenario.id}-lead-value`} labelText="Potential value range" value={lead.potentialValueRange} onChange={(event) => setLead({ ...lead, potentialValueRange: event.target.value })} />
            <TextInput id={`${scenario.id}-lead-pain`} labelText="Pain severity" value={lead.painSeverity} onChange={(event) => setLead({ ...lead, painSeverity: event.target.value })} />
            <TextArea id={`${scenario.id}-lead-signals`} className={styles.fullWidth} labelText="Visible signals (one per line: signal | category)" rows={3} value={lead.signals.map((signal) => `${signal.label} | ${signal.category}`).join('\n')} onChange={(event) => setLead({ ...lead, signals: parseSignals(event.target.value) })} />
          </div>
          {(createLead.isError || updateLead.isError) && <InlineNotification kind="error" title="Lead could not be saved" subtitle="Check required lead fields and try again." />}
          <div className={styles.inlineActions}>
            <Button size="sm" renderIcon={editingLeadId ? Edit : Add} disabled={createLead.isPending || updateLead.isPending || !lead.companyName.trim() || !lead.industry.trim()} onClick={() => editingLeadId ? updateLead.mutate({ leadId: editingLeadId, request: lead }, { onSuccess: resetLeadForm }) : createLead.mutate(lead, { onSuccess: resetLeadForm })}>{editingLeadId ? 'Save lead' : 'Add lead'}</Button>
            {editingLeadId && <Button size="sm" kind="tertiary" onClick={resetLeadForm}>Cancel</Button>}
          </div>
        </section>
      </>}
    </Stack>
  )
}
