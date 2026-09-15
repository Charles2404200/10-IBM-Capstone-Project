import { Button, Select, SelectItem, TextInput } from '@carbon/react'
import { Add, TrashCan } from '@carbon/icons-react'
import type { LifecycleConditionNode, LifecycleConditionType } from '@/api/types'

const conditions: LifecycleConditionType[] = [
  'LEAD_SELECTED', 'MIN_EVIDENCE_COUNT', 'HAS_HYPOTHESIS', 'MIN_RESEARCH_CONFIDENCE',
  'OUTREACH_ACCEPTED', 'PREPARATION_READY', 'MIN_PREPARATION_SCORE', 'MEETING_STARTED',
  'MEETING_COMPLETED', 'MIN_TRUST', 'MIN_INTEREST', 'MIN_PATIENCE', 'PROPOSAL_CREATED',
  'PROPOSAL_SUBMITTED', 'CLIENT_DECISION_AVAILABLE', 'ASSESSMENT_AVAILABLE', 'CURRENT_STAGE_COMPLETED',
]
const thresholds = new Set<LifecycleConditionType>([
  'MIN_EVIDENCE_COUNT', 'MIN_RESEARCH_CONFIDENCE', 'MIN_PREPARATION_SCORE', 'MIN_TRUST', 'MIN_INTEREST', 'MIN_PATIENCE',
])
const label = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
const emptyLeaf = (): LifecycleConditionNode => ({ kind: 'LEAF', conditionType: 'LEAD_SELECTED', threshold: null, value: null })

export default function LifecycleConditionEditor({ id, value, disabled, depth = 1, labelText, onChange }: {
  id: string
  value: LifecycleConditionNode | null
  disabled: boolean
  depth?: number
  labelText?: string
  onChange: (value: LifecycleConditionNode | null) => void
}) {
  const mode = value?.kind ?? 'NONE'
  return <div style={{ borderLeft: depth > 1 ? '2px solid #c6c6c6' : undefined, paddingLeft: depth > 1 ? '.75rem' : 0 }}>
    <Select id={`${id}-mode`} labelText={depth === 1 ? labelText ?? 'Additional completion condition' : 'Condition'} value={mode}
      disabled={disabled} onChange={(event) => {
        if (event.target.value === 'NONE') onChange(null)
        else if (event.target.value === 'GROUP') onChange({ kind: 'GROUP', operator: 'AND', children: [emptyLeaf()] })
        else onChange(emptyLeaf())
      }}>
      <SelectItem value="NONE" text="No additional condition" />
      <SelectItem value="LEAF" text="Single condition" />
      {depth < 4 && <SelectItem value="GROUP" text="Condition group" />}
    </Select>
    {value?.kind === 'LEAF' && <div style={{ display: 'grid', gridTemplateColumns: 'minmax(12rem, 1fr) 8rem', gap: '.75rem', marginTop: '.75rem' }}>
      <Select id={`${id}-type`} labelText="Condition type" value={value.conditionType} disabled={disabled}
        onChange={(event) => { const conditionType = event.target.value as LifecycleConditionType
          onChange({ kind: 'LEAF', conditionType, threshold: thresholds.has(conditionType) ? 1 : null, value: null }) }}>
        {conditions.map((condition) => <SelectItem key={condition} value={condition} text={label(condition)} />)}
      </Select>
      {thresholds.has(value.conditionType) && <TextInput id={`${id}-threshold`} type="number" labelText="Threshold"
        min={0} max={value.conditionType === 'MIN_EVIDENCE_COUNT' ? 1000 : 100} value={value.threshold ?? 0} disabled={disabled}
        onChange={(event) => onChange({ ...value, threshold: Number(event.target.value) })} />}
    </div>}
    {value?.kind === 'GROUP' && <div style={{ marginTop: '.75rem', display: 'grid', gap: '.75rem' }}>
      <Select id={`${id}-operator`} labelText="Group operator" value={value.operator} disabled={disabled}
        onChange={(event) => onChange({ ...value, operator: event.target.value as 'AND' | 'OR' })}>
        <SelectItem value="AND" text="All conditions (AND)" /><SelectItem value="OR" text="Any condition (OR)" />
      </Select>
      {value.children.map((child, index) => <div key={`${id}-${index}`} style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '.5rem' }}>
        <LifecycleConditionEditor id={`${id}-${index}`} value={child} disabled={disabled} depth={depth + 1}
          onChange={(next) => onChange({ ...value, children: next === null ? value.children.filter((_item, i) => i !== index)
            : value.children.map((item, i) => i === index ? next : item) })} />
        <Button hasIconOnly size="sm" kind="ghost" renderIcon={TrashCan} iconDescription="Remove condition"
          disabled={disabled || value.children.length === 1} onClick={() => onChange({ ...value, children: value.children.filter((_item, i) => i !== index) })} />
      </div>)}
      <Button size="sm" kind="ghost" renderIcon={Add} disabled={disabled || value.children.length >= 10}
        onClick={() => onChange({ ...value, children: [...value.children, emptyLeaf()] })}>Add condition</Button>
    </div>}
  </div>
}
