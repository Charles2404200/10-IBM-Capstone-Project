import { useEffect, useMemo, useState } from 'react'
import { Button, Checkbox, InlineLoading, InlineNotification, Stack, TextArea, TextInput, Tile } from '@carbon/react'
import { useScenarioLifecycle, useUpdateScenarioLifecycle } from '@/api/hooks/useAdminScenarios'
import type { ScenarioLifecycleDefinition, ScenarioStageDefinition } from '@/api/types'
import { getProblemDetail } from '@/api/problemDetails'
import ScenarioObjectiveEditor from './ScenarioObjectiveEditor'
import LifecycleConditionEditor from './LifecycleConditionEditor'

function validate(definition: ScenarioLifecycleDefinition): string[] {
  const errors: string[] = []
  const keys = new Set<string>()
  definition.stages.forEach((stage) => {
    if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(stage.key)) errors.push(`Stage ${stage.label || stage.capability} needs a stable uppercase key.`)
    if (keys.has(stage.key)) errors.push(`Stage key ${stage.key} is duplicated.`)
    keys.add(stage.key)
    if (!stage.label.trim()) errors.push(`Stage ${stage.key} needs a label.`)
  })
  const objectiveKeys = new Set<string>()
  definition.objectives.forEach((objective) => {
    if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(objective.key)) errors.push('Every objective needs a stable uppercase key.')
    if (objectiveKeys.has(objective.key)) errors.push(`Objective key ${objective.key} is duplicated.`)
    objectiveKeys.add(objective.key)
    if (!objective.title.trim()) errors.push(`Objective ${objective.key} needs a title.`)
    if (objective.stageKey && !keys.has(objective.stageKey)) errors.push(`Objective ${objective.key} refers to a missing stage.`)
  })
  return [...new Set(errors)]
}

export default function ScenarioLifecycleEditor({ scenarioId, editable }: { scenarioId: string; editable: boolean }) {
  const query = useScenarioLifecycle(scenarioId)
  const update = useUpdateScenarioLifecycle(scenarioId)
  const [definition, setDefinition] = useState<ScenarioLifecycleDefinition | null>(null)
  const [version, setVersion] = useState<number | null>(null)
  const [dirty, setDirty] = useState(false)
  useEffect(() => {
    if (query.data && !dirty) { setDefinition(query.data.definition); setVersion(query.data.version) }
  }, [query.data, dirty])
  const errors = useMemo(() => definition ? validate(definition) : [], [definition])
  const change = (next: ScenarioLifecycleDefinition) => { setDefinition(next); setDirty(true) }
  const replaceStage = (index: number, stage: ScenarioStageDefinition) => {
    if (!definition) return
    change({ ...definition, stages: definition.stages.map((item, itemIndex) => itemIndex === index ? stage : item) })
  }

  if (query.isLoading) return <InlineLoading description="Loading lifecycle and objectives" />
  if (query.isError || !definition || version === null) return <Stack gap={3}>
    <InlineNotification kind="error" title="Lifecycle unavailable"
      subtitle="The saved lifecycle could not be loaded. Retry before editing; no fallback was applied." />
    <Button size="sm" onClick={() => void query.refetch()}>Retry</Button>
  </Stack>

  return <Tile><Stack gap={5}>
    <div><p>05 · Lifecycle &amp; objectives</p><h5>Learner journey</h5>
      <p>Labels and gates belong to this scenario revision. Application capabilities and their dependency order remain fixed.</p></div>
    {!editable && <InlineNotification kind="info" title="Read-only published lifecycle"
      subtitle="Create a draft revision to change stages or objectives." hideCloseButton />}
    {definition.stages.map((stage, index) => <div key={stage.key} style={{ borderTop: '1px solid #c6c6c6', paddingTop: '1rem' }}>
      <Stack gap={3}>
        <strong>{index + 1}. {stage.capability}</strong>
        <div style={{ display: 'flex', gap: '.5rem' }} title="Technical capability dependencies fix this stage order">
          <Button size="sm" kind="ghost" disabled>Move up</Button>
          <Button size="sm" kind="ghost" disabled>Move down</Button>
        </div>
        <TextInput id={`stage-key-${index}`} labelText="Stable stage key" maxLength={64} value={stage.key} disabled={!editable}
          onChange={(event) => replaceStage(index, { ...stage, key: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_') })} />
        <TextInput id={`stage-label-${index}`} labelText="Learner-facing label" maxLength={100} value={stage.label} disabled={!editable}
          onChange={(event) => replaceStage(index, { ...stage, label: event.target.value })} />
        <TextArea id={`stage-description-${index}`} labelText="Description" maxLength={2000} rows={2} value={stage.description} disabled={!editable}
          onChange={(event) => replaceStage(index, { ...stage, description: event.target.value })} />
        <TextArea id={`stage-goal-${index}`} labelText="Goal" maxLength={2000} rows={2} value={stage.goal} disabled={!editable}
          onChange={(event) => replaceStage(index, { ...stage, goal: event.target.value })} />
        <TextArea id={`stage-done-${index}`} labelText="Done guidance" maxLength={2000} rows={2} value={stage.doneText} disabled={!editable}
          onChange={(event) => replaceStage(index, { ...stage, doneText: event.target.value })} />
        <TextArea id={`stage-next-${index}`} labelText="Next guidance" maxLength={2000} rows={2} value={stage.nextText} disabled={!editable}
          onChange={(event) => replaceStage(index, { ...stage, nextText: event.target.value })} />
        <Checkbox id={`stage-required-${index}`} labelText={stage.capability === 'MEETING_REVIEW' ? 'Required stage gate' : 'Required by technical lifecycle'}
          checked={stage.required} disabled={!editable || stage.capability !== 'MEETING_REVIEW'}
          onChange={(_event, state) => replaceStage(index, { ...stage, required: Boolean(state.checked) })} />
        <LifecycleConditionEditor id={`stage-entry-${index}`} value={stage.entryCondition} disabled={!editable}
          labelText="Additional entry condition"
          onChange={(entryCondition) => replaceStage(index, { ...stage, entryCondition })} />
        <LifecycleConditionEditor id={`stage-condition-${index}`} value={stage.completionCondition} disabled={!editable}
          onChange={(completionCondition) => replaceStage(index, { ...stage, completionCondition })} />
      </Stack>
    </div>)}
    <ScenarioObjectiveEditor definition={definition} onChange={change} disabled={!editable} />
    {errors.length > 0 && <InlineNotification kind="error" title="Fix lifecycle validation errors"
      subtitle={errors.join(' ')} hideCloseButton />}
    {update.isError && <InlineNotification kind="error" title="Lifecycle could not be saved"
      subtitle={getProblemDetail(update.error, 'Review the definition and try again. If another author saved first, reload their version before applying your edits.')} />}
    {update.isSuccess && !dirty && <InlineNotification kind="success" title="Lifecycle saved" subtitle="The draft revision now uses this definition." hideCloseButton />}
    <div style={{ display: 'flex', gap: '.75rem' }}>
      <Button size="sm" disabled={!editable || !dirty || errors.length > 0 || update.isPending}
        onClick={() => update.mutate({ definition, version }, { onSuccess: (saved) => {
          setDefinition(saved.definition); setVersion(saved.version); setDirty(false)
        } })}>Save lifecycle &amp; objectives</Button>
      {dirty && <Button size="sm" kind="ghost" onClick={() => {
        if (query.data) { setDefinition(query.data.definition); setVersion(query.data.version); setDirty(false) }
      }}>Discard local changes</Button>}
    </div>
  </Stack></Tile>
}
