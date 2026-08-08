import {
  Button,
  Select,
  SelectItem,
  TextInput,
  NumberInput,
  Tile,
  Stack,
} from '@carbon/react'
import { Add, TrashCan } from '@carbon/icons-react'
import type { ConditionNode, ConditionType, LogicalOperator } from '@/api/types'

const CONDITION_TYPE_OPTIONS: { value: ConditionType; label: string }[] = [
  { value: 'MIN_ENGAGEMENTS_COMPLETED', label: 'Minimum engagements completed' },
  { value: 'MIN_ENGAGEMENTS_WON', label: 'Minimum engagements won' },
  { value: 'MIN_BEST_OVERALL_SCORE', label: 'Minimum best overall score' },
  { value: 'MIN_AVERAGE_OVERALL_SCORE', label: 'Minimum average overall score' },
  { value: 'MIN_COMPETENCY_SCORE', label: 'Minimum competency score' },
  { value: 'MIN_DISTINCT_SCENARIOS_COMPLETED', label: 'Minimum distinct scenarios completed' },
  { value: 'MIN_WIN_RATE_PERCENT', label: 'Minimum win rate (%)' },
]

function newLeaf(): ConditionNode {
  return { kind: 'LEAF', operator: null, children: null, type: 'MIN_ENGAGEMENTS_COMPLETED', competencyName: null, threshold: 1 }
}

function newGroup(): ConditionNode {
  return { kind: 'GROUP', operator: 'AND', children: [newLeaf()], type: null, competencyName: null, threshold: null }
}

interface Props {
  node: ConditionNode
  onChange: (node: ConditionNode) => void
  onRemove?: () => void
  /** Stable dot-separated path (e.g. "0.1.2") used to derive unique, non-random field ids. */
  path?: string
}

/** Recursive editor for an achievement rule tree (AND/OR groups of leaf conditions).
 *  Mirrors the backend's flat ConditionNode DTO exactly so the payload round-trips
 *  without any client-side transformation. */
export default function AchievementRuleBuilder({ node, onChange, onRemove, path = '0' }: Props) {
  const isGroup = node.kind === 'GROUP'
  const depth = path.split('.').length - 1

  const updateChild = (index: number, child: ConditionNode) => {
    const children = [...(node.children ?? [])]
    children[index] = child
    onChange({ ...node, children })
  }

  const removeChild = (index: number) => {
    const children = (node.children ?? []).filter((_, i) => i !== index)
    onChange({ ...node, children })
  }

  const addLeafChild = () => onChange({ ...node, children: [...(node.children ?? []), newLeaf()] })
  const addGroupChild = () => onChange({ ...node, children: [...(node.children ?? []), newGroup()] })

  return (
    <Tile style={{ marginLeft: depth > 0 ? '1.5rem' : 0, borderLeft: depth > 0 ? '3px solid #0f62fe' : undefined }}>
      <Stack gap={4}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <Select
            id={`kind-${path}`}
            labelText="Node type"
            value={node.kind}
            onChange={(e) => {
              const kind = e.target.value as ConditionNode['kind']
              onChange(kind === 'GROUP' ? newGroup() : newLeaf())
            }}
            size="sm"
          >
            <SelectItem value="LEAF" text="Condition" />
            <SelectItem value="GROUP" text="Group (AND / OR)" />
          </Select>

          {isGroup && (
            <Select
              id={`operator-${path}`}
              labelText="Logic"
              value={node.operator ?? 'AND'}
              onChange={(e) => onChange({ ...node, operator: e.target.value as LogicalOperator })}
              size="sm"
            >
              <SelectItem value="AND" text="ALL must be true (AND)" />
              <SelectItem value="OR" text="ANY may be true (OR)" />
            </Select>
          )}

          {onRemove && (
            <Button kind="danger--ghost" size="sm" renderIcon={TrashCan} onClick={onRemove}>
              Remove
            </Button>
          )}
        </div>

        {isGroup ? (
          <Stack gap={4}>
            {(node.children ?? []).map((child, index) => (
              <AchievementRuleBuilder
                key={index}
                node={child}
                onChange={(updated) => updateChild(index, updated)}
                onRemove={(node.children?.length ?? 0) > 1 ? () => removeChild(index) : undefined}
                path={`${path}.${index}`}
              />
            ))}
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <Button kind="tertiary" size="sm" renderIcon={Add} onClick={addLeafChild}>
                Add condition
              </Button>
              <Button kind="tertiary" size="sm" renderIcon={Add} onClick={addGroupChild}>
                Add nested group
              </Button>
            </div>
          </Stack>
        ) : (
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <Select
              id={`type-${path}`}
              labelText="Condition"
              value={node.type ?? 'MIN_ENGAGEMENTS_COMPLETED'}
              onChange={(e) => onChange({ ...node, type: e.target.value as ConditionType })}
              size="sm"
            >
              {CONDITION_TYPE_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value} text={opt.label} />
              ))}
            </Select>

            {node.type === 'MIN_COMPETENCY_SCORE' && (
              <TextInput
                id={`competency-${path}`}
                labelText="Competency name"
                value={node.competencyName ?? ''}
                onChange={(e) => onChange({ ...node, competencyName: e.target.value })}
                size="sm"
              />
            )}

            <NumberInput
              id={`threshold-${path}`}
              label="Threshold"
              value={node.threshold ?? 0}
              min={0}
              onChange={(_e, state) => onChange({ ...node, threshold: Number(state?.value ?? 0) })}
              size="sm"
            />
          </div>
        )}
      </Stack>
    </Tile>
  )
}
