import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Button,
  Tag,
  ProgressBar,
  Modal,
  RadioButtonGroup,
  RadioButton,
} from '@carbon/react'
import { ArrowRight, Add } from '@carbon/icons-react'
import { useMyEngagements, useStartEngagement } from '@/api/hooks/useEngagements'
import { useScenarios } from '@/api/hooks/useScenarios'
import { usePortfolioSummary } from '@/api/hooks/usePortfolio'
import { useAuthStore } from '@/store/authStore'
import { resolveEngagementRoute } from '@/api/engagementRouting'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { Engagement, ScenarioSummary } from '@/api/types'
import styles from './CommandCentrePage.module.scss'

/** Cockpit engagement card: phase, progress, next action, and mini-metrics —
 *  everything the learner needs to know at a glance ("home cockpit" concept). */
function EngagementCard({ engagement }: { engagement: Engagement }) {
  const navigate = useNavigate()
  const isDecision = engagement.state === 'CLIENT_DECISION' || engagement.state === 'REVIEW'
  const isComplete = engagement.state === 'COMPLETED'
  const continueRoute = resolveEngagementRoute(engagement)

  return (
    <div
      className={styles.engagementCard}
      role="button"
      tabIndex={0}
      onClick={() => navigate(continueRoute)}
      onKeyDown={(e) => e.key === 'Enter' && navigate(continueRoute)}
    >
      <div className={styles.engagementCardTitleRow}>
        <h4 className={styles.engagementCardTitle}>
          {engagement.scenarioTitle ?? 'Untitled Engagement'}
        </h4>
        <Tag type={isComplete ? 'gray' : isDecision ? 'purple' : 'blue'} size="sm">
          {isComplete ? 'Completed' : isDecision ? 'Decision' : 'In Progress'}
        </Tag>
      </div>

      <div className={styles.engagementCardIndustryRow}>
        <Tag type="cyan" size="sm">{engagement.scenarioIndustry ?? '—'}</Tag>
        {engagement.leadCompanyName && (
          <span style={{ color: '#525252', fontSize: '0.8125rem' }}>{engagement.leadCompanyName}</span>
        )}
      </div>

      <div>
        <div className={styles.engagementProgressLabel}>
          <span>{engagement.phaseLabel}</span>
          <span>{engagement.progressPercent}%</span>
        </div>
        <ProgressBar
          label="Progress"
          value={engagement.progressPercent}
          max={100}
          size="small"
          hideLabel
        />
      </div>

      <div className={styles.engagementNextAction}>
        <span className="label">Next action</span>
        <span className="value">{engagement.nextAction}</span>
      </div>

      <div className={styles.engagementMetaGrid}>
        <div className={styles.engagementMetaItem}>
          <span className={styles.engagementMetaLabel}>Evidence</span>
          <span className={styles.engagementMetaValue}>{engagement.evidenceCount}</span>
        </div>
        <div className={styles.engagementMetaItem}>
          <span className={styles.engagementMetaLabel}>Days elapsed</span>
          <span className={styles.engagementMetaValue}>{engagement.daysElapsed}</span>
        </div>
      </div>

      <div className={styles.engagementCardFooter}>
        Continue <ArrowRight size={16} />
      </div>
    </div>
  )
}

function StarRating({ value }: { value: number }) {
  return (
    <span className={styles.difficultyStars}>
      {'★'.repeat(value)}{'☆'.repeat(Math.max(0, 5 - value))}
    </span>
  )
}

/** Pre-engagement briefing (role, objective, success criteria) so learners
 *  understand the mission before jumping into the Lead Pipeline. */
function ScenarioBriefingModal({
  scenario,
  onCancel,
  onConfirm,
  isPending,
}: {
  scenario: ScenarioSummary
  onCancel: () => void
  onConfirm: () => void
  isPending: boolean
}) {
  const { briefing, difficultyProfile } = scenario
  return (
    <Modal
      open
      modalHeading={scenario.title}
      modalLabel="Scenario Briefing"
      primaryButtonText="Start Engagement"
      secondaryButtonText="Cancel"
      onRequestClose={onCancel}
      onRequestSubmit={onConfirm}
      primaryButtonDisabled={isPending}
      size="md"
    >
      <div className={styles.briefingMetaRow}>
        <div>
          <span style={{ color: '#525252', fontSize: '0.75rem', display: 'block' }}>Your role</span>
          <strong style={{ color: '#161616' }}>{briefing.consultantRole}</strong>
        </div>
        <div>
          <span style={{ color: '#525252', fontSize: '0.75rem', display: 'block' }}>Industry</span>
          <strong style={{ color: '#161616' }}>{scenario.industry}</strong>
        </div>
        <div>
          <span style={{ color: '#525252', fontSize: '0.75rem', display: 'block' }}>Simulated time</span>
          <strong style={{ color: '#161616' }}>{briefing.simulatedDays} days</strong>
        </div>
      </div>

      <div className={styles.briefingSection}>
        <h5>Objective</h5>
        <p>{briefing.objective}</p>
      </div>

      {(briefing.successCriteria ?? []).length > 0 && (
        <div className={styles.briefingSection}>
          <h5>Success criteria</h5>
          <ul>
            {(briefing.successCriteria ?? []).map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ul>
        </div>
      )}

      <div className={styles.briefingSection}>
        <h5>Difficulty</h5>
        <div className={styles.difficultyDimensions}>
          <div className={styles.difficultyDimensionRow}>
            <span>Information ambiguity</span>
            <StarRating value={difficultyProfile.informationAmbiguity} />
          </div>
          <div className={styles.difficultyDimensionRow}>
            <span>Stakeholder complexity</span>
            <StarRating value={difficultyProfile.stakeholderComplexity} />
          </div>
          <div className={styles.difficultyDimensionRow}>
            <span>Commercial pressure</span>
            <StarRating value={difficultyProfile.commercialPressure} />
          </div>
        </div>
      </div>
    </Modal>
  )
}

export default function CommandCentrePage() {
  const { displayName } = useAuthStore()
  const navigate = useNavigate()
  const { data: engagements, isLoading: engLoading, isError: engError } = useMyEngagements()
  const { data: scenarios, isLoading: scenLoading } = useScenarios()
  const { data: portfolio } = usePortfolioSummary()
  const startEngagement = useStartEngagement()
  const [personaPickerScenario, setPersonaPickerScenario] = useState<ScenarioSummary | null>(null)
  const [selectedPersonaId, setSelectedPersonaId] = useState('')
  const [briefingScenario, setBriefingScenario] = useState<ScenarioSummary | null>(null)

  const beginEngagement = (scenarioId: string, personaId?: string) => {
    startEngagement.mutate(
      { scenarioId, personaId },
      { onSuccess: (engagement) => navigate(`/dashboard/engagements/${engagement.id}/leads`) },
    )
  }

  const handleStart = (scenario: ScenarioSummary) => {
    setBriefingScenario(scenario)
  }

  const confirmBriefing = () => {
    if (!briefingScenario) return
    const scenario = briefingScenario
    setBriefingScenario(null)
    if (scenario.personas.length > 1) {
      setSelectedPersonaId(scenario.personas[0].id)
      setPersonaPickerScenario(scenario)
      return
    }
    beginEngagement(scenario.id)
  }

  const confirmPersonaSelection = () => {
    if (!personaPickerScenario) return
    beginEngagement(personaPickerScenario.id, selectedPersonaId)
    setPersonaPickerScenario(null)
  }

  if (engLoading || scenLoading) return <LoadingState />
  if (engError) return <ErrorState />

  const activeEngagements = engagements?.filter((e) => e.state !== 'COMPLETED') ?? []
  const winRate = portfolio && portfolio.completedEngagements > 0
    ? Math.round((portfolio.contractsWon / portfolio.completedEngagements) * 100)
    : 0

  return (
    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={6}>
          <div className={styles.header}>
            <div>
              <Heading>Command Centre</Heading>
              <p className={styles.subheading}>Welcome back, {displayName}. Ready to close a deal?</p>
            </div>
          </div>

          {portfolio && (
            <div className={styles.performanceStrip}>
              <div className={styles.performanceStat}>
                <span className={styles.performanceStatLabel}>Engagements</span>
                <span className={styles.performanceStatValue}>{portfolio.totalEngagements}</span>
              </div>
              <div className={styles.performanceStat}>
                <span className={styles.performanceStatLabel}>Completed</span>
                <span className={styles.performanceStatValue}>{portfolio.completedEngagements}</span>
              </div>
              <div className={styles.performanceStat}>
                <span className={styles.performanceStatLabel}>Win rate</span>
                <span className={styles.performanceStatValue}>{winRate}%</span>
              </div>
              <div className={styles.performanceStat}>
                <span className={styles.performanceStatLabel}>Avg. score</span>
                <span className={styles.performanceStatValue}>{Math.round(portfolio.averageOverallScore)}</span>
              </div>
            </div>
          )}

          {activeEngagements.length > 0 && (
            <section>
              <h3 style={{ marginBottom: '1rem', color: '#161616' }}>Active Engagements</h3>
              <Grid narrow>
                {activeEngagements.map((e) => (
                  <Column key={e.id} lg={5} md={4} sm={4} style={{ marginBottom: '1rem' }}>
                    <EngagementCard engagement={e} />
                  </Column>
                ))}
              </Grid>
            </section>
          )}

          <section>
            <h3 style={{ marginBottom: '1rem', color: '#161616' }}>Available Scenarios</h3>
            <Grid narrow>
              {scenarios?.map((scenario) => (
                <Column key={scenario.id} lg={5} md={4} sm={4} style={{ marginBottom: '1rem' }}>
                  <div className={styles.engagementCard}>
                    <div className={styles.scenarioTags}>
                      <Tag type="cyan" size="sm">{scenario.industry}</Tag>
                      <Tag type="gray" size="sm">
                        <StarRating value={scenario.difficulty} />
                      </Tag>
                    </div>
                    <h4 className={styles.engagementCardTitle}>{scenario.title}</h4>
                    <p style={{ color: '#525252', fontSize: '0.875rem', flexGrow: 1 }}>
                      {scenario.description?.slice(0, 120)}…
                    </p>
                    <Button
                      renderIcon={Add}
                      size="sm"
                      disabled={startEngagement.isPending}
                      onClick={() => handleStart(scenario)}
                    >
                      Start Engagement
                    </Button>
                  </div>
                </Column>
              ))}
            </Grid>
          </section>
        </Stack>
      </Column>

      {briefingScenario && (
        <ScenarioBriefingModal
          scenario={briefingScenario}
          onCancel={() => setBriefingScenario(null)}
          onConfirm={confirmBriefing}
          isPending={startEngagement.isPending}
        />
      )}

      {personaPickerScenario && (
        <Modal
          open
          modalHeading="Choose a stakeholder persona"
          modalLabel={personaPickerScenario.title}
          primaryButtonText="Start Engagement"
          secondaryButtonText="Cancel"
          onRequestClose={() => setPersonaPickerScenario(null)}
          onRequestSubmit={confirmPersonaSelection}
          primaryButtonDisabled={!selectedPersonaId || startEngagement.isPending}
        >
          <p style={{ color: '#525252', marginBottom: '1rem' }}>
            This scenario has multiple stakeholder personalities. Pick who you&apos;ll be engaging with.
          </p>
          <RadioButtonGroup
            name="persona-picker"
            orientation="vertical"
            valueSelected={selectedPersonaId}
            onChange={(value) => setSelectedPersonaId(String(value))}
          >
            {(personaPickerScenario.personas ?? []).map((persona) => (
              <RadioButton
                key={persona.id}
                id={`persona-${persona.id}`}
                value={persona.id}
                labelText={`${persona.name} — ${persona.jobTitle}`}
              />
            ))}
          </RadioButtonGroup>
        </Modal>
      )}
    </Grid>
  )
}
