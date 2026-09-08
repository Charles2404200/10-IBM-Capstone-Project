import { describe, expect, it } from 'vitest'
import type { PersonaSummary, ScenarioSummary } from './types'

describe('scenario response contract', () => {
  it('uses contentVersion and permits optional learner-visible persona details', () => {
    const persona: PersonaSummary = {
      id: 'persona-1',
      name: 'Client',
      jobTitle: 'CIO',
      organisation: 'Example Co',
      communicationStyle: null,
      visibleConcerns: null,
    }
    const scenario = {
      contentVersion: 3,
      personas: [persona],
    } satisfies Pick<ScenarioSummary, 'contentVersion' | 'personas'>

    expect(scenario.contentVersion).toBe(3)
    expect(scenario.personas[0].communicationStyle).toBeNull()
  })
})
