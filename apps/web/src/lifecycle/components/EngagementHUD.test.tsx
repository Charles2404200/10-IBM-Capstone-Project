import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Engagement } from '@/api/types'
import EngagementHUD from './EngagementHUD'

const configuredEngagement: Engagement = {
  id: 'engagement-1', userId: 'user-1', scenarioId: 'scenario-1', personaId: 'persona-1',
  state: 'CLIENT_INTELLIGENCE', selectedLeadId: 'lead-1', createdAt: '2026-09-15T00:00:00Z',
  completedAt: null, events: [], scenarioTitle: 'Scenario', scenarioIndustry: 'Technology',
  leadCompanyName: 'Client', phase: 'CLIENT_INTELLIGENCE', phaseLabel: 'Client Intelligence',
  progressPercent: 20, nextAction: 'Continue research', evidenceCount: 2, daysElapsed: 1, meetingId: null,
  lifecycle: {
    currentStageKey: 'INVESTIGATE', currentStageIndex: 1, totalStages: 3, progressPercent: 20,
    stages: [
      { key: 'CHOOSE', capability: 'LEAD', label: 'Pick an opportunity', description: '', goal: '', doneText: '', nextText: '', required: true, status: 'COMPLETED' },
      { key: 'INVESTIGATE', capability: 'CLIENT_INTELLIGENCE', label: 'Investigate the client', description: '', goal: '', doneText: '', nextText: '', required: true, status: 'CURRENT' },
      { key: 'CONTACT', capability: 'OUTREACH', label: 'Contact the client', description: '', goal: '', doneText: '', nextText: '', required: true, status: 'LOCKED' },
    ],
  },
  objectives: [{ key: 'HYPOTHESIS', parentObjectiveKey: null, stageKey: 'INVESTIGATE', title: 'Build a grounded hypothesis',
    description: '', required: true, completed: false, completionExplanation: 'Required: has hypothesis' }],
}

let engagement = configuredEngagement

vi.mock('@/api/hooks/useEngagements', () => ({
  useEngagement: () => ({ data: engagement }),
  useMyEngagements: () => ({ data: [engagement] }),
}))
vi.mock('@/api/hooks/useMeeting', () => ({ usePersonaState: () => ({ data: undefined }) }))

describe('EngagementHUD configured lifecycle', () => {
  it('renders server labels, objective progress, and locks unavailable stages', () => {
    engagement = configuredEngagement
    render(<MemoryRouter initialEntries={['/dashboard/engagements/engagement-1/intelligence']}><EngagementHUD /></MemoryRouter>)
    expect(screen.getAllByText('Investigate the client').length).toBeGreaterThan(0)
    expect(screen.getByText('Build a grounded hypothesis')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Pick an opportunity/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /Contact the client/ })).toBeDisabled()
  })

  it('keeps legacy engagements usable when the server omits the resolved lifecycle', () => {
    engagement = { ...configuredEngagement, lifecycle: undefined, objectives: undefined }
    render(<MemoryRouter initialEntries={['/dashboard/engagements/engagement-1/intelligence']}><EngagementHUD /></MemoryRouter>)

    expect(screen.getAllByText('Research the client').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /Make contact/ })).toBeDisabled()
  })
})
