import { describe, expect, it } from 'vitest'
import type { PersonaSummary, ScenarioSummary } from './types'

describe('scenario response contract', () => {
  it('uses version and required learner-visible persona strings', () => {
    const persona: PersonaSummary = {
      id: 'persona-1',
      name: 'Client',
      jobTitle: 'CIO',
      organisation: 'Example Co',
      communicationStyle: '',
      visibleConcerns: '',
    }
    const scenario = {
      version: 3,
      personas: [persona],
    } satisfies Pick<ScenarioSummary, 'version' | 'personas'>

    expect(scenario.version).toBe(3)
    expect(scenario.personas[0].communicationStyle).toBe('')
  })
})
