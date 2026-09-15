import { Button, Checkbox, Select, SelectItem, Stack, TextArea, TextInput } from '@carbon/react'
import { Add, ArrowDown, ArrowUp, TrashCan } from '@carbon/icons-react'
import type { ScenarioLifecycleDefinition, ScenarioObjectiveDefinition } from '@/api/types'
import LifecycleConditionEditor from './LifecycleConditionEditor'

function freshObjective(index: number): ScenarioObjectiveDefinition {
  return {
    key: `OBJECTIVE_${index + 1}`,
    parentObjectiveKey: null,
    stageKey: null,
    title: '',
    description: '',
    required: true,
    displayOrder: index,
    completionCondition: null,
  }
}

export default function ScenarioObjectiveEditor({ definition, onChange, disabled }: {
  definition: ScenarioLifecycleDefinition
  onChange: (definition: ScenarioLifecycleDefinition) => void
  disabled: boolean
}) {
  const replace = (index: number, objective: ScenarioObjectiveDefinition) => onChange({
    ...definition,
    objectives: definition.objectives.map((item, itemIndex) => itemIndex === index ? objective : item),
  })
  const move = (index: number, offset: number) => {
    const target = index + offset
    if (target < 0 || target >= definition.objectives.length) return
    const objectives = [...definition.objectives]
    ;[objectives[index], objectives[target]] = [objectives[target], objectives[index]]
    onChange({ ...definition, objectives: objectives.map((item, displayOrder) => ({ ...item, displayOrder })) })
  }

  return (
    <Stack gap={4}>
      <div><h6>Objectives and sub-objectives</h6><p>Required objectives become server-enforced completion gates.</p></div>
      {definition.objectives.map((objective, index) => (
        <div key={`${objective.key}-${index}`} style={{ borderLeft: '3px solid #8a3ffc', paddingLeft: '1rem' }}>
          <Stack gap={3}>
            <TextInput id={`objective-key-${index}`} labelText="Stable objective key" maxLength={64}
              value={objective.key} disabled={disabled}
              onChange={(event) => replace(index, { ...objective, key: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_') })} />
            <TextInput id={`objective-title-${index}`} labelText="Learner-facing title" maxLength={150}
              value={objective.title} disabled={disabled}
              onChange={(event) => replace(index, { ...objective, title: event.target.value })} />
            <TextArea id={`objective-description-${index}`} labelText="Description" maxLength={2000} rows={2}
              value={objective.description} disabled={disabled}
              onChange={(event) => replace(index, { ...objective, description: event.target.value })} />
            <Select id={`objective-stage-${index}`} labelText="Associated stage" value={objective.stageKey ?? ''}
              disabled={disabled} onChange={(event) => replace(index, { ...objective, stageKey: event.target.value || null })}>
              <SelectItem value="" text="Scenario-wide (checked at completion)" />
              {definition.stages.map((stage) => <SelectItem key={stage.key} value={stage.key} text={stage.label} />)}
            </Select>
            <Select id={`objective-parent-${index}`} labelText="Parent objective" value={objective.parentObjectiveKey ?? ''}
              disabled={disabled} onChange={(event) => replace(index, { ...objective, parentObjectiveKey: event.target.value || null })}>
              <SelectItem value="" text="Main objective" />
              {definition.objectives.filter((candidate) => candidate.key !== objective.key)
                .map((candidate) => <SelectItem key={candidate.key} value={candidate.key} text={candidate.title || candidate.key} />)}
            </Select>
            <Checkbox id={`objective-required-${index}`} labelText="Required for completion" checked={objective.required}
              disabled={disabled} onChange={(_event, state) => replace(index, { ...objective, required: Boolean(state.checked) })} />
            <LifecycleConditionEditor id={`objective-condition-${index}`} value={objective.completionCondition}
              disabled={disabled} onChange={(completionCondition) => replace(index, { ...objective, completionCondition })} />
            <div style={{ display: 'flex', gap: '.5rem' }}>
              <Button hasIconOnly kind="ghost" size="sm" renderIcon={ArrowUp} iconDescription="Move objective up"
                disabled={disabled || index === 0} onClick={() => move(index, -1)} />
              <Button hasIconOnly kind="ghost" size="sm" renderIcon={ArrowDown} iconDescription="Move objective down"
                disabled={disabled || index === definition.objectives.length - 1} onClick={() => move(index, 1)} />
              <Button hasIconOnly kind="ghost" size="sm" renderIcon={TrashCan} iconDescription="Remove objective"
                disabled={disabled} onClick={() => onChange({ ...definition, objectives: definition.objectives
                  .filter((_item, itemIndex) => itemIndex !== index)
                  .map((item, displayOrder) => ({ ...item, displayOrder, parentObjectiveKey:
                    item.parentObjectiveKey === objective.key ? null : item.parentObjectiveKey })) })} />
            </div>
          </Stack>
        </div>
      ))}
      <Button size="sm" kind="tertiary" renderIcon={Add} disabled={disabled || definition.objectives.length >= 50}
        onClick={() => onChange({ ...definition, objectives: [...definition.objectives, freshObjective(definition.objectives.length)] })}>
        Add objective
      </Button>
    </Stack>
  )
}
