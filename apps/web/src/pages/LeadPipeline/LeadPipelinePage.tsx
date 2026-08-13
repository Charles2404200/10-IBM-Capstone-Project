import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Stack,
  Button,
  Tag,
  Tile,
  InlineNotification,
} from '@carbon/react'
import { ArrowRight } from '@carbon/icons-react'
import { useEngagement } from '@/api/hooks/useEngagements'
import { useLeads, useSelectLead } from '@/api/hooks/useLeads'
import { useScenario } from '@/api/hooks/useScenarios'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { LeadSummary } from '@/api/types'
import { PHASE_LABEL } from '@/lifecycle/phases'
import PageHeader from '@/lifecycle/components/PageHeader'
import shell from '@/lifecycle/lifecycle.module.scss'

const DIFFICULTY_TYPE = { EASY: 'green', MEDIUM: 'magenta', HARD: 'red' } as const

function LeadCard({
  lead,
  onSelect,
  isSelected,
  isSelecting,
  selectionLocked,
}: {
  lead: LeadSummary
  onSelect: () => void
  isSelected: boolean
  isSelecting: boolean
  selectionLocked: boolean
}) {
  return (
    <Tile style={{ border: isSelected ? '1px solid #0f62fe' : undefined }}>
      <Stack gap={4}>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <h4 style={{ color: '#161616' }}>{lead.companyName}</h4>
          <Tag type={DIFFICULTY_TYPE[lead.difficulty]}>{lead.difficulty}</Tag>
        </div>
        <Tag type="gray">{lead.industry}</Tag>
        <p style={{ color: '#525252', fontSize: '0.875rem' }}>{lead.publicDescription}</p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
          {lead.signals.map((s) => (
            <Tag key={s.id} type="teal" size="sm">
              {s.label}
            </Tag>
          ))}
        </div>
        <p style={{ color: '#525252', fontSize: '0.75rem', fontStyle: 'italic' }}>
          Decision maker, budget, and potential value are unknown until you research this client.
        </p>
        {isSelected && <Tag type="blue">Selected</Tag>}
        {!isSelected && selectionLocked && (
          <Tag type="gray">Another lead already selected</Tag>
        )}
        {!isSelected && !selectionLocked && (
          <Button
            renderIcon={ArrowRight}
            size="sm"
            disabled={isSelecting}
            onClick={onSelect}
          >
            Investigate Lead
          </Button>
        )}
      </Stack>
    </Tile>
  )
}

export default function LeadPipelinePage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()

  const { data: engagement, isLoading: engLoading } = useEngagement(engagementId!)
  const { data: leads, isLoading: leadsLoading, isError } = useLeads(engagement?.scenarioId ?? '')
  const { data: scenario } = useScenario(engagement?.scenarioId ?? '')
  const selectLead = useSelectLead(engagementId!)

  if (engLoading || leadsLoading) return <LoadingState />
  if (isError) return <ErrorState />

  const canSelect = engagement?.state === 'QUALIFYING'
  const alreadySelected = engagement?.selectedLeadId
  const selectionLocked = Boolean(alreadySelected)

  const handleSelect = (leadId: string) => {
    selectLead.mutate(leadId, {
      onSuccess: () => navigate(`/dashboard/engagements/${engagementId}/intelligence`),
    })
  }

  return (
    <>
    <PageHeader
      phase="LEAD"
      description="Review available leads. Signals are visible — hidden details emerge through research."
    />
    <Grid fullWidth style={{ padding: '1rem 2rem 2rem' }} className={shell.fixedShellBody}>
      <Column lg={16} md={8} sm={4} className={shell.fixedShellFrame}>
        <Stack gap={7} className={shell.scrollPanel}>

          {scenario && (
            <Tile style={{ background: '#f4f4f4' }}>
              <div style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap' }}>
                <div>
                  <span style={{ color: '#525252', fontSize: '0.75rem', display: 'block' }}>Information ambiguity</span>
                  <strong style={{ color: '#161616' }}>{'★'.repeat(scenario.difficultyProfile.informationAmbiguity)}{'☆'.repeat(5 - scenario.difficultyProfile.informationAmbiguity)}</strong>
                </div>
                <div>
                  <span style={{ color: '#525252', fontSize: '0.75rem', display: 'block' }}>Stakeholder complexity</span>
                  <strong style={{ color: '#161616' }}>{'★'.repeat(scenario.difficultyProfile.stakeholderComplexity)}{'☆'.repeat(5 - scenario.difficultyProfile.stakeholderComplexity)}</strong>
                </div>
                <div>
                  <span style={{ color: '#525252', fontSize: '0.75rem', display: 'block' }}>Commercial pressure</span>
                  <strong style={{ color: '#161616' }}>{'★'.repeat(scenario.difficultyProfile.commercialPressure)}{'☆'.repeat(5 - scenario.difficultyProfile.commercialPressure)}</strong>
                </div>
              </div>
            </Tile>
          )}

          {selectLead.isError && (
            <InlineNotification
              kind="error"
              title="Could not select lead"
              subtitle="Please try again."
              hideCloseButton
            />
          )}

          {selectionLocked && (
            <Stack gap={4}>
              <InlineNotification
                kind="info"
                title="Lead already selected"
                subtitle={`This engagement has already locked in a lead — continue to ${PHASE_LABEL.CLIENT_INTELLIGENCE} to keep researching it.`}
                hideCloseButton
                lowContrast
              />
              <Button
                kind="ghost"
                size="sm"
                renderIcon={ArrowRight}
                onClick={() => navigate(`/dashboard/engagements/${engagementId}/intelligence`)}
              >
                Continue to {PHASE_LABEL.CLIENT_INTELLIGENCE}
              </Button>
            </Stack>
          )}

          {!canSelect && !alreadySelected && (
            <InlineNotification
              kind="info"
              title="Lead selection not available"
              subtitle={`Current state: ${engagement?.state}`}
              hideCloseButton
            />
          )}

          <Grid narrow>
            {leads?.map((lead) => (
              <Column key={lead.id} lg={5} md={4} sm={4} style={{ marginBottom: '1.5rem' }}>
                <LeadCard
                  lead={lead}
                  onSelect={() => handleSelect(lead.id)}
                  isSelected={alreadySelected === lead.id}
                  isSelecting={selectLead.isPending}
                  selectionLocked={selectionLocked}
                />
              </Column>
            ))}
          </Grid>
        </Stack>
      </Column>
    </Grid>
    </>
  )
}
