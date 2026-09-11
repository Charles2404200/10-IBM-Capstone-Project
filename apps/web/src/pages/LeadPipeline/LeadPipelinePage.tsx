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
import styles from './LeadPipelinePage.module.scss'

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
    <Tile className={`${styles.leadCard} ${isSelected ? styles.leadCardSelected : ''}`}>
      <Stack gap={4}>
        <div className={styles.leadCardHeader}>
          <h4 className={styles.leadCompanyName}>{lead.companyName}</h4>
          <Tag
            type={DIFFICULTY_TYPE[lead.difficulty]}
            className={styles.difficultyTag}
          >
            {lead.difficulty}
          </Tag>
        </div>
        <Tag type="gray">{lead.industry}</Tag>
        <p className={styles.leadDescription}>{lead.publicDescription}</p>
        <div className={styles.signalTags}>
          {lead.signals.map((s) => (
            <Tag key={s.id} type="teal" size="sm" className={styles.signalTag}>
              {s.label.charAt(0).toUpperCase() + s.label.slice(1)}
            </Tag>
          ))}
        </div>
        <p className={styles.leadNote}>
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
    <Grid fullWidth className={`${shell.fixedShellBody} ${styles.pageGrid}`}>
      <Column lg={16} md={8} sm={4} className={shell.fixedShellFrame}>
        <Stack gap={7} >

          {scenario && (
          <Grid narrow>
            <Column lg={15} md={8} sm={4}>
              <Tile className={styles.scenarioTile}>
                <div className={styles.difficultyDetails}>
                  <div>
                    <span className={styles.difficultyLabel}>Information ambiguity</span>
                    <strong className={styles.difficultyStars}>{'★'.repeat(scenario.difficultyProfile.informationAmbiguity)}{'☆'.repeat(5 - scenario.difficultyProfile.informationAmbiguity)}</strong>
                  </div>
                  <div>
                    <span className={styles.difficultyLabel}>Stakeholder complexity</span>
                    <strong className={styles.difficultyStars}>{'★'.repeat(scenario.difficultyProfile.stakeholderComplexity)}{'☆'.repeat(5 - scenario.difficultyProfile.stakeholderComplexity)}</strong>
                  </div>
                  <div>
                    <span className={styles.difficultyLabel}>Commercial pressure</span>
                    <strong className={styles.difficultyStars}>{'★'.repeat(scenario.difficultyProfile.commercialPressure)}{'☆'.repeat(5 - scenario.difficultyProfile.commercialPressure)}</strong>
                  </div>
                </div>
              </Tile>
            </Column>
          </Grid>
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
            <Grid narrow>
              <Column lg={15} md={8} sm={4}>
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
              </Column>
            </Grid>
          )}

          {!canSelect && !alreadySelected && (
            <InlineNotification
              kind="info"
              title="Lead selection not available"
              subtitle={`Current state: ${engagement?.state}`}
              hideCloseButton
            />
          )}

          <Grid narrow className={styles.leadList}>
            {leads?.map((lead) => (
              <Column key={lead.id} lg={5} md={4} sm={4} className={styles.leadColumn}>
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