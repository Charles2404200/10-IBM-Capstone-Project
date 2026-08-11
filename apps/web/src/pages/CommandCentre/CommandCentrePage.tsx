import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Heading,
  Modal,
  ProgressBar,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  Stack,
  Tag,
  TextInput,
} from '@carbon/react'
import { Add, ArrowRight, Renew, Search } from '@carbon/icons-react'
import { useMyEngagements, useStartEngagement } from '@/api/hooks/useEngagements'
import { useScenarios } from '@/api/hooks/useScenarios'
import { usePortfolioSummary } from '@/api/hooks/usePortfolio'
import { useAuthStore } from '@/store/authStore'
import { resolveEngagementRoute } from '@/api/engagementRouting'
import { isActiveEngagement, requiresMeetingRetry } from '@/features/engagement/services/engagementLifecycleService'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { CompletedEngagementView, Engagement, ScenarioSummary } from '@/api/types'
import styles from './CommandCentrePage.module.scss'
import { PHASE_COUNT, PHASE_LABEL } from '@/lifecycle/phases'

type EngagementStatus = 'ACTION_REQUIRED' | 'AWAITING_RESPONSE' | 'READY_FOR_REVIEW' | 'COMPLETED'
type StatusFilter = 'ALL' | EngagementStatus
type SortMode = 'RECENT' | 'PROGRESS' | 'SCENARIO'

const STATUS_META: Record<EngagementStatus, { label: string; tag: 'blue' | 'cyan' | 'purple' | 'gray' }> = {
  ACTION_REQUIRED: { label: 'Action required', tag: 'blue' },
  AWAITING_RESPONSE: { label: 'Awaiting response', tag: 'cyan' },
  READY_FOR_REVIEW: { label: 'Ready for review', tag: 'purple' },
  COMPLETED: { label: 'Completed', tag: 'gray' },
}

function statusOf(engagement: Engagement): EngagementStatus {
  if (engagement.state === 'COMPLETED') return 'COMPLETED'
  if (engagement.state === 'REVIEW' || engagement.state === 'CLIENT_DECISION') return 'READY_FOR_REVIEW'
  if (engagement.state === 'PROPOSAL_SUBMITTED') return 'AWAITING_RESPONSE'
  return 'ACTION_REQUIRED'
}

function latestActivity(engagement: Engagement): number {
  const lastEvent = engagement.events?.[engagement.events.length - 1]?.occurredAt
  return new Date(lastEvent ?? engagement.createdAt).getTime()
}

function attemptLabels(engagements: Engagement[]) {
  const groups = new Map<string, Engagement[]>()
  engagements.forEach((engagement) => {
    const key = `${engagement.scenarioTitle ?? engagement.scenarioId}|${engagement.leadCompanyName ?? 'unselected'}`
    groups.set(key, [...(groups.get(key) ?? []), engagement])
  })

  const labels = new Map<string, string>()
  groups.forEach((items) => {
    if (items.length < 2) return
    [...items]
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
      .forEach((engagement, index) => labels.set(engagement.id, `Attempt #${index + 1}`))
  })
  return labels
}

/** Uses the real lifecycle length rather than a hard-coded 8, which disagreed
 *  with the ten-step stepper now shown on every workspace — the same screen was
 *  telling the learner two different things about how long the job is. */
function phasesComplete(engagement: Engagement) {
  return Math.min(
    PHASE_COUNT,
    Math.max(1, Math.ceil((engagement.progressPercent / 100) * PHASE_COUNT))
  )
}

function EngagementStatusTag({ engagement }: { engagement: Engagement }) {
  const meta = STATUS_META[statusOf(engagement)]
  return <Tag type={meta.tag} size="sm">{meta.label}</Tag>
}

function StarRating({ value }: { value: number }) {
  return (
    <span className={styles.difficultyStars}>
      {'*'.repeat(value)}{'-'.repeat(Math.max(0, 5 - value))}
    </span>
  )
}

function FeaturedEngagement({
  engagement,
  attemptLabel,
}: {
  engagement: Engagement
  attemptLabel?: string
}) {
  const navigate = useNavigate()
  return (
    <section className={styles.featuredPanel}>
      <div className={styles.sectionEyebrow}>Continue where you left off</div>
      <div className={styles.featuredContent}>
        <div className={styles.featuredMain}>
          <div className={styles.featuredTitleRow}>
            <div>
              <h2>{engagement.scenarioTitle ?? 'Untitled Engagement'}</h2>
              <div className={styles.featuredMeta}>
                <Tag type="cyan" size="sm">{engagement.scenarioIndustry ?? 'Unassigned'}</Tag>
                {engagement.leadCompanyName && <span>{engagement.leadCompanyName}</span>}
                {attemptLabel && <Tag type="gray" size="sm">{attemptLabel}</Tag>}
              </div>
            </div>
            <EngagementStatusTag engagement={engagement} />
          </div>

          <div className={styles.featuredProgressBlock}>
            <div className={styles.progressLabel}>
              <span>{PHASE_LABEL[engagement.phase] ?? engagement.phaseLabel}</span>
              <span>{engagement.progressPercent}%</span>
            </div>
            <ProgressBar label="Progress" hideLabel value={engagement.progressPercent} max={100} size="small" />
            <span className={styles.phaseCount}>{phasesComplete(engagement)} of {PHASE_COUNT} phases</span>
          </div>

          <div className={styles.nextActionBlock}>
            <span className={styles.blockLabel}>Next action</span>
            <p>{engagement.nextAction}</p>
          </div>

          <div className={styles.featuredFacts}>
            <span><strong>{engagement.evidenceCount}</strong> evidence</span>
            <span><strong>{engagement.daysElapsed}</strong> days elapsed</span>
          </div>
        </div>

        <div className={styles.featuredCta}>
          <Button renderIcon={ArrowRight} onClick={() => navigate(resolveEngagementRoute(engagement))}>
            Continue engagement
          </Button>
        </div>
      </div>
    </section>
  )
}

function CompactEngagementRow({
  engagement,
  attemptLabel,
}: {
  engagement: Engagement
  attemptLabel?: string
}) {
  const navigate = useNavigate()
  return (
    <button className={styles.compactRow} type="button" onClick={() => navigate(resolveEngagementRoute(engagement))}>
      <div className={styles.compactPrimary}>
        <div className={styles.compactTitleLine}>
          <h4>{engagement.scenarioTitle ?? 'Untitled Engagement'}</h4>
          {attemptLabel && <Tag type="gray" size="sm">{attemptLabel}</Tag>}
        </div>
        <div className={styles.compactMeta}>
          <Tag type="cyan" size="sm">{engagement.scenarioIndustry ?? 'Unassigned'}</Tag>
          {engagement.leadCompanyName && <span>{engagement.leadCompanyName}</span>}
        </div>
        <span className={styles.compactNext}>{engagement.nextAction}</span>
      </div>

      <div className={styles.compactProgress}>
        <div className={styles.progressLabel}>
          <span>{PHASE_LABEL[engagement.phase] ?? engagement.phaseLabel}</span>
          <span>{engagement.progressPercent}%</span>
        </div>
        <ProgressBar label="Progress" hideLabel value={engagement.progressPercent} max={100} size="small" />
      </div>

      <div className={styles.compactStatus}>
        <EngagementStatusTag engagement={engagement} />
        <ArrowRight size={18} />
      </div>
    </button>
  )
}

function CompletedRow({ engagement }: { engagement: CompletedEngagementView }) {
  const navigate = useNavigate()
  const successful = engagement.outcome === 'PROPOSAL_ACCEPTED' || engagement.outcome === 'WON'
  return (
    <button
      type="button"
      className={styles.completedRow}
      onClick={() => navigate(`/dashboard/engagements/${engagement.engagementId}/assessment`)}
    >
      <div>
        <h4>{engagement.scenarioTitle}</h4>
        <div className={styles.compactMeta}>
          <Tag type="cyan" size="sm">{engagement.industry}</Tag>
          <span>{engagement.completedAt ? new Date(engagement.completedAt).toLocaleDateString() : 'In review'}</span>
        </div>
      </div>
      <div className={styles.completedScore}>
        <span>{engagement.overallScore}/100</span>
        <Tag type={successful ? 'green' : 'red'} size="sm">
          {engagement.outcome.replace(/_/g, ' ')}
        </Tag>
      </div>
    </button>
  )
}

function FailedMeetingRow({ engagement, attemptLabel }: { engagement: Engagement; attemptLabel?: string }) {
  const navigate = useNavigate()
  return (
    <button
      className={styles.failedMeetingRow}
      type="button"
      onClick={() => navigate(resolveEngagementRoute(engagement))}
    >
      <div>
        <div className={styles.compactTitleLine}>
          <h4>{engagement.scenarioTitle ?? 'Untitled Engagement'}</h4>
          {attemptLabel && <Tag type="gray" size="sm">{attemptLabel}</Tag>}
        </div>
        <div className={styles.compactMeta}>
          <Tag type="cyan" size="sm">{engagement.scenarioIndustry ?? 'Unassigned'}</Tag>
          {engagement.leadCompanyName && <span>{engagement.leadCompanyName}</span>}
        </div>
        <span className={styles.compactNext}>{engagement.nextAction}</span>
      </div>
      <div className={styles.failedMeetingAction}>
        <Tag type="red" size="sm">Meeting failed</Tag>
        <span>Review and retry</span>
        <ArrowRight size={18} />
      </div>
    </button>
  )
}

function ScenarioCard({
  scenario,
  onStart,
  isPending,
}: {
  scenario: ScenarioSummary
  onStart: () => void
  isPending: boolean
}) {
  return (
    <div className={styles.scenarioCard}>
      <div className={styles.scenarioTags}>
        <Tag type="cyan" size="sm">{scenario.industry}</Tag>
        <Tag type="gray" size="sm"><StarRating value={scenario.difficulty} /></Tag>
      </div>
      <h4>{scenario.title}</h4>
      <p>{scenario.description}</p>
      <Button renderIcon={Add} size="sm" disabled={isPending} onClick={onStart}>
        Start Engagement
      </Button>
    </div>
  )
}

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
          <span>Your role</span>
          <strong>{briefing.consultantRole}</strong>
        </div>
        <div>
          <span>Industry</span>
          <strong>{scenario.industry}</strong>
        </div>
        <div>
          <span>Simulated time</span>
          <strong>{briefing.simulatedDays} days</strong>
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
            {(briefing.successCriteria ?? []).map((criterion) => (
              <li key={criterion}>{criterion}</li>
            ))}
          </ul>
        </div>
      )}

      <div className={styles.briefingSection}>
        <h5>Difficulty</h5>
        <div className={styles.difficultyDimensions}>
          <div><span>Information ambiguity</span><StarRating value={difficultyProfile.informationAmbiguity} /></div>
          <div><span>Stakeholder complexity</span><StarRating value={difficultyProfile.stakeholderComplexity} /></div>
          <div><span>Commercial pressure</span><StarRating value={difficultyProfile.commercialPressure} /></div>
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
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [sortMode, setSortMode] = useState<SortMode>('RECENT')
  const [searchTerm, setSearchTerm] = useState('')

  const allEngagements = useMemo(() => engagements ?? [], [engagements])
  const labelsByEngagement = useMemo(() => attemptLabels(allEngagements), [allEngagements])

  const activeEngagements = useMemo(
    () => allEngagements.filter(isActiveEngagement),
    [allEngagements],
  )
  const failedMeetingEngagements = useMemo(
    () => allEngagements.filter(requiresMeetingRetry).sort((a, b) => latestActivity(b) - latestActivity(a)),
    [allEngagements],
  )

  const featuredEngagement = useMemo(
    () => [...activeEngagements].sort((a, b) => latestActivity(b) - latestActivity(a))[0],
    [activeEngagements],
  )

  const filteredActiveEngagements = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()
    return activeEngagements
      .filter((engagement) => engagement.id !== featuredEngagement?.id)
      .filter((engagement) => statusFilter === 'ALL' || statusOf(engagement) === statusFilter)
      .filter((engagement) => {
        if (!query) return true
        return [
          engagement.scenarioTitle,
          engagement.scenarioIndustry,
          engagement.leadCompanyName,
          PHASE_LABEL[engagement.phase] ?? engagement.phaseLabel,
          engagement.nextAction,
        ].some((value) => value?.toLowerCase().includes(query))
      })
      .sort((a, b) => {
        if (sortMode === 'PROGRESS') return b.progressPercent - a.progressPercent
        if (sortMode === 'SCENARIO') return (a.scenarioTitle ?? '').localeCompare(b.scenarioTitle ?? '')
        return latestActivity(b) - latestActivity(a)
      })
  }, [activeEngagements, featuredEngagement?.id, searchTerm, sortMode, statusFilter])

  const completedHistory = portfolio?.completedEngagementsHistory ?? []
  const activeCount = activeEngagements.length
  const successfulOutcomes = portfolio?.contractsWon ?? 0

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

  return (
    <main className={styles.page}>
      <Stack gap={7}>
        <header className={styles.header}>
          <div>
            <Heading>Command Centre</Heading>
            <p className={styles.subheading}>
              Welcome back, {displayName}. Continue your current engagement or start a new scenario.
            </p>
          </div>
          <Button
            renderIcon={Add}
            onClick={() => document.getElementById('available-scenarios')?.scrollIntoView({ behavior: 'smooth' })}
          >
            Start New Scenario
          </Button>
        </header>

        {portfolio && (
          <div className={styles.performanceStrip}>
            <div className={styles.performanceStat}>
              <span className={styles.performanceStatLabel}>Active</span>
              <span className={styles.performanceStatValue}>{activeCount}</span>
            </div>
            <div className={styles.performanceStat}>
              <span className={styles.performanceStatLabel}>Completed</span>
              <span className={styles.performanceStatValue}>{portfolio.completedEngagements}</span>
            </div>
            <div className={styles.performanceStat}>
              <span className={styles.performanceStatLabel}>Successful outcomes</span>
              <span className={styles.performanceStatValue}>{successfulOutcomes} / {portfolio.completedEngagements}</span>
            </div>
            <div className={styles.performanceStat}>
              <span className={styles.performanceStatLabel}>Avg. score</span>
              <span className={styles.performanceStatValue}>{Math.round(portfolio.averageOverallScore)} / 100</span>
            </div>
          </div>
        )}

        {featuredEngagement && (
          <FeaturedEngagement
            engagement={featuredEngagement}
            attemptLabel={labelsByEngagement.get(featuredEngagement.id)}
          />
        )}

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <div>
              <h3>Active Engagements</h3>
              <p>Compact view of everything still in flight.</p>
            </div>
            <div className={styles.controls}>
              <TextInput
                id="engagement-search"
                labelText="Search engagements"
                hideLabel
                placeholder="Search engagements"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
              />
              <Select
                id="status-filter"
                labelText="Filter"
                hideLabel
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
              >
                <SelectItem value="ALL" text="All active" />
                <SelectItem value="ACTION_REQUIRED" text="Action required" />
                <SelectItem value="AWAITING_RESPONSE" text="Awaiting response" />
                <SelectItem value="READY_FOR_REVIEW" text="Ready for review" />
              </Select>
              <Select
                id="sort-mode"
                labelText="Sort"
                hideLabel
                value={sortMode}
                onChange={(event) => setSortMode(event.target.value as SortMode)}
              >
                <SelectItem value="RECENT" text="Recently active" />
                <SelectItem value="PROGRESS" text="Progress" />
                <SelectItem value="SCENARIO" text="Scenario" />
              </Select>
            </div>
          </div>

          <div className={styles.compactList}>
            {filteredActiveEngagements.length > 0 ? (
              filteredActiveEngagements.map((engagement) => (
                <CompactEngagementRow
                  key={engagement.id}
                  engagement={engagement}
                  attemptLabel={labelsByEngagement.get(engagement.id)}
                />
              ))
            ) : (
              <div className={styles.emptyState}>
                <Search size={20} />
                <span>No active engagements match this view.</span>
              </div>
            )}
          </div>
        </section>

        {failedMeetingEngagements.length > 0 && (
          <section className={styles.failedMeetingsSection}>
            <div className={styles.sectionHeader}>
              <div>
                <h3>Meeting attempts requiring retry</h3>
                <p>These attempts are closed. Review the debrief before restarting the same lead.</p>
              </div>
            </div>
            <div className={styles.compactList}>
              {failedMeetingEngagements.map((engagement) => (
                <FailedMeetingRow
                  key={engagement.id}
                  engagement={engagement}
                  attemptLabel={labelsByEngagement.get(engagement.id)}
                />
              ))}
            </div>
          </section>
        )}

        {completedHistory.length > 0 && (
          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <div>
                <h3>Recently Completed</h3>
                <p>Review outcomes and scores from completed training runs.</p>
              </div>
            </div>
            <div className={styles.completedList}>
              {completedHistory.slice(0, 4).map((engagement) => (
                <CompletedRow key={engagement.engagementId} engagement={engagement} />
              ))}
            </div>
          </section>
        )}

        <section id="available-scenarios" className={styles.section}>
          <div className={styles.sectionHeader}>
            <div>
              <h3>Available Scenarios</h3>
              <p>Start a fresh run when you are ready to practise another situation.</p>
            </div>
          </div>
          <div className={styles.scenarioGrid}>
            {(scenarios ?? []).map((scenario) => (
              <ScenarioCard
                key={scenario.id}
                scenario={scenario}
                onStart={() => handleStart(scenario)}
                isPending={startEngagement.isPending}
              />
            ))}
          </div>
        </section>

        {completedHistory.length > 0 && scenarios?.[0] && (
          <section className={styles.recommendationPanel}>
            <div>
              <div className={styles.sectionEyebrow}>Recommended for you</div>
              <h3>{scenarios[0].title}</h3>
              <p>
                Recommended because recent reviews can be strengthened by practising stakeholder discovery
                and commercial evidence gathering in another scenario.
              </p>
            </div>
            <Button kind="secondary" renderIcon={Renew} onClick={() => handleStart(scenarios[0])}>
              Start recommended scenario
            </Button>
          </section>
        )}
      </Stack>

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
          <p className={styles.modalHelp}>
            This scenario has multiple stakeholder personalities. Pick who you will be engaging with.
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
                labelText={`${persona.name} - ${persona.jobTitle}`}
              />
            ))}
          </RadioButtonGroup>
        </Modal>
      )}
    </main>
  )
}
