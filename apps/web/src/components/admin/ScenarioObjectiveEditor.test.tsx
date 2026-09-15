import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ScenarioLifecycleDefinition, ScenarioObjectiveDefinition } from '@/api/types'
import ScenarioObjectiveEditor from './ScenarioObjectiveEditor'

const objective = (key: string, title: string, displayOrder: number): ScenarioObjectiveDefinition => ({
  key,
  parentObjectiveKey: null,
  stageKey: 'DISCOVERY',
  title,
  description: '',
  required: true,
  displayOrder,
  completionCondition: null,
})

const definition: ScenarioLifecycleDefinition = {
  schemaVersion: 1,
  stages: [{
    key: 'DISCOVERY', capability: 'CLIENT_INTELLIGENCE', label: 'Discovery', description: '', goal: '',
    doneText: '', nextText: '', required: true, displayOrder: 0, entryCondition: null, completionCondition: null,
  }],
  objectives: [objective('FIRST', 'First objective', 0), objective('SECOND', 'Second objective', 1)],
}

describe('ScenarioObjectiveEditor', () => {
  it('adds an objective with a stable generated key', () => {
    const onChange = vi.fn()
    render(<ScenarioObjectiveEditor definition={definition} onChange={onChange} disabled={false} />)

    fireEvent.click(screen.getByRole('button', { name: 'Add objective' }))

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
      objectives: [...definition.objectives, expect.objectContaining({ key: 'OBJECTIVE_3', displayOrder: 2 })],
    }))
  })

  it('removes an objective and clears child references to it', () => {
    const onChange = vi.fn()
    const nested = {
      ...definition,
      objectives: [definition.objectives[0], { ...definition.objectives[1], parentObjectiveKey: 'FIRST' }],
    }
    render(<ScenarioObjectiveEditor definition={nested} onChange={onChange} disabled={false} />)

    fireEvent.click(screen.getAllByRole('button', { name: 'Remove objective' })[0])

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
      objectives: [expect.objectContaining({ key: 'SECOND', parentObjectiveKey: null, displayOrder: 0 })],
    }))
  })

  it('reorders objectives and normalises their display order', () => {
    const onChange = vi.fn()
    render(<ScenarioObjectiveEditor definition={definition} onChange={onChange} disabled={false} />)

    fireEvent.click(screen.getAllByRole('button', { name: 'Move objective down' })[0])

    const updated = onChange.mock.calls[0][0] as ScenarioLifecycleDefinition
    expect(updated.objectives.map(({ key, displayOrder }) => ({ key, displayOrder }))).toEqual([
      { key: 'SECOND', displayOrder: 0 },
      { key: 'FIRST', displayOrder: 1 },
    ])
  })
})
