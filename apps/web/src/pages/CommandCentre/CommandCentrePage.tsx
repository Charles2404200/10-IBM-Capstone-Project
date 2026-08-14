import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Heading,
  Modal,
  ProgressBar,
  Pagination,
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
import { useScenarioCatalog, useScenarioCatalogIndustries } from '@/api/hooks/useScenarios'
import { usePortfolioSummary } from '@/api/hooks/usePortfolio'
import { useAuthStore } from '@/store/authStore'
import { resolveEngagementRoute } from '@/api/engagementRouting'
import { isActiveEngagement, requiresMeetingRetry } from '@/features/engagement/services/engagementLifecycleService'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { Engagement, ScenarioSummary } from '@/api/types'
import styles from './CommandCentrePage.module.scss'
import { PHASE_COUNT, PHASE_LABEL } from '@/lifecycle/phases'
import { useExperience } from '@/lifecycle/experience'

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

function RecentlyCompletedRow({ item }: { item: import('@/api/types').CompletedEngagementView }) {
  const navigate = useNavigate()
  return (
    <button className={styles.recentlyCompletedRow} type="button" onClick={() => navigate(`/dashboard/engagements/${item.engagementId}/assessment`)}>
      <div>
        <h4>{item.scenarioTitle}</h4>
        <div className={styles.compactMeta}>
          <Tag type="cyan" size="sm">{item.industry}</Tag>
          <span>{item.completedAt ? new Date(item.completedAt).toLocaleDateString() : 'Completed'}</span>
        </div>
      </div>
      <div className={styles.recentlyCompletedResult}>
        <strong>{item.overallScore}/100</strong>
        <Tag type={item.outcome.includes('REJECTED') || item.outcome.includes('LOST') ? 'red' : 'green'} size="sm">
          {item.outcome.replaceAll('_', ' ')}
        </Tag>
      </div>
    </button>
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

/** How many scenario cards to render at once. Enough to browse, few enough to
 *  keep the page a fixed height regardless of how big the catalogue gets. */

/**
 * What a brand-new account sees instead of a dashboard built for history.
 *
 * Three lines, then one button. The three lines are the shape of the whole
 * engagement, in the same words the stepper and every page title use, so the
 * first thing learned is the vocabulary everything else is written in. They
 * say what the arc is, not what to click — pointing at the next decision is
 * the one thing this must not do, because that decision is what gets scored.
 */
function FirstRunPanel({
  scenario,
  onStart,
  isPending,
}: {
  scenario: ScenarioSummary | null
  onStart: () => void
  isPending: boolean
}) {
  return (
    <section className={styles.firstRun} aria-labelledby="first-run-heading">
      <div className={styles.sectionEyebrow}>Start here</div>
      <h2 id="first-run-heading" className={styles.firstRunHeading}>
        You are a consultant. Win the work.
      </h2>
      <ol className={styles.firstRunArc}>
        <li>
          <strong>{PHASE_LABEL.CLIENT_INTELLIGENCE}</strong> — gather evidence before you say anything.
        </li>
        <li>
          <strong>{PHASE_LABEL.OUTREACH}</strong> — earn a meeting, then run it.
        </li>
        <li>
          <strong>{PHASE_LABEL.PROPOSAL}</strong> — put a case to them and live with their answer.
        </li>
      </ol>
      <p className={styles.firstRunNote}>
        Nothing here is undoable practice with a safety net — the client reacts to what you actually
        write, and your review at the end is built from those reactions.
      </p>

      {scenario ? (
        <div className={styles.firstRunStarter}>
          <div>
            <div className={styles.sectionEyebrow}>Your first client</div>
            <h3>{scenario.title}</h3>
            <p>{scenario.industry}</p>
          </div>
          <Button renderIcon={ArrowRight} onClick={onStart} disabled={isPending}>
            {isPending ? 'Starting…' : 'Start your first engagement'}
          </Button>
        </div>
      ) : (
        <p className={styles.firstRunNote}>No scenarios are available yet. Check back shortly.</p>
      )}

      <button
        type="button"
        className={styles.firstRunAlt}
        onClick={() => document.getElementById('available-scenarios')?.scrollIntoView({ behavior: 'smooth' })}
      >
        Or choose a different client
      </button>
    </section>
  )
}

export default function CommandCentrePage() {
  const { displayName } = useAuthStore()
  const navigate = useNavigate()
  const { data: engagements, isLoading: engLoading, isError: engError } = useMyEngagements()
  const { data: portfolio } = usePortfolioSummary()
  const startEngagement = useStartEngagement()
  const [personaPickerScenario, setPersonaPickerScenario] = useState<ScenarioSummary | null>(null)
  const [selectedPersonaId, setSelectedPersonaId] = useState('')
  const [briefingScenario, setBriefingScenario] = useState<ScenarioSummary | null>(null)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [sortMode, setSortMode] = useState<SortMode>('RECENT')
  const [searchTerm, setSearchTerm] = useState('')
  const [catalogueSearch, setCatalogueSearch] = useState('')
  const [catalogueIndustry, setCatalogueIndustry] = useState('')
  const [catalogueDifficulty, setCatalogueDifficulty] = useState<number | ''>('')
  const [cataloguePage, setCataloguePage] = useState(1)
  const catalogueFilters = useMemo(() => ({
    search: catalogueSearch.trim() || undefined,
    industry: catalogueIndustry || undefined,
    difficulty: catalogueDifficulty || undefined,
    page: cataloguePage - 1,
    size: 9,
  }), [catalogueDifficulty, catalogueIndustry, cataloguePage, catalogueSearch])
  const { data: scenarioCatalogue, isLoading: scenLoading, isFetching: catalogueLoading, isError: scenarioError } = useScenarioCatalog(catalogueFilters)
  const { data: catalogIndustries = [] } = useScenarioCatalogIndustries()

  useEffect(() => { setCataloguePage(1) }, [catalogueSearch, catalogueIndustry, catalogueDifficulty])

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

  const beginEngagement = (scenario: ScenarioSummary, personaId?: string) => {
    startEngagement.mutate(
      { scenarioId: scenario.id, personaId, scenario },
      { onSuccess: ({ engagement }) => navigate(`/dashboard/engagements/${engagement.id}/leads`) },
    )
  }

  // The catalogue is unbounded — the shared dev backend currently holds over

  const emptyListMessage = (() => {
    if (activeEngagements.length === 0) return 'No engagements running. Start a scenario below.'
    if (searchTerm.trim() || statusFilter !== 'ALL') return 'No engagements match this search.'
    if (featuredEngagement) return 'Your only active engagement is the one shown above.'
    return 'No active engagements.'
  })()

  const { stage } = useExperience()

  /**
   * One scenario, not two thousand. A newcomer has no basis for choosing
   * between them, so offering the choice is not generosity — it is the first
   * decision the product asks them to make and the one they are least equipped
   * for. The catalogue stays one click away for anyone who wants it.
   */
  const starterScenario = scenarioCatalogue?.items[0] ?? null

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
    beginEngagement(scenario)
  }

  const confirmPersonaSelection = () => {
    if (!personaPickerScenario) return
    beginEngagement(personaPickerScenario, selectedPersonaId)
    setPersonaPickerScenario(null)
  }

  if (engLoading || scenLoading) return <LoadingState />
  if (engError || scenarioError) return <ErrorState />

  return (
    <main className={styles.page}>
      <Stack gap={7}>
        <header className={styles.header}>
          <div>
            <Heading>Command Centre</Heading>
            <p className={styles.subheading}>
              {stage === 'FIRST_VISIT'
                ? `Welcome, ${displayName}. This is where every client engagement starts and finishes.`
                : `Welcome back, ${displayName}. Continue your current engagement or start a new scenario.`}
            </p>
          </div>
          {stage !== 'FIRST_VISIT' && (
            <Button renderIcon={Add} onClick={() => document.getElementById('scenario-catalogue')?.scrollIntoView({ behavior: 'smooth' })}>
              Start new scenario
            </Button>
          )}
        </header>

        {/* Four zeros say nothing and read as a report card of failures the
            person has not had the chance to earn yet. */}
        {portfolio && portfolio.totalEngagements > 0 && (
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

        <div className={styles.browseBody}>
        {stage === 'FIRST_VISIT' && (
          <FirstRunPanel
            scenario={starterScenario}
            onStart={() => starterScenario && handleStart(starterScenario)}
            isPending={startEngagement.isPending}
          />
        )}

        {/* An empty dashboard is a heading, a description, a search box and a
            sentence saying there is nothing there -- chrome between a newcomer
            and the one button they need. */}
        {stage !== 'FIRST_VISIT' && (
        <div className={styles.dashboardGrid}>
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
                  <span>{emptyListMessage}</span>
                </div>
              )}
            </div>
          </section>

          <aside className={styles.dashboardAside}>
            <section className={styles.sidePanel}>
              <div className={styles.sidePanelHeader}>
                <div>
                  <h3>Recently completed</h3>
                  <p>Review outcomes from your last runs.</p>
                </div>
                <button type="button" className={styles.textAction} onClick={() => navigate('/dashboard/portfolio')}>View all</button>
              </div>
              {completedHistory.length > 0 ? (
                <div className={styles.recentlyCompletedList}>
                  {completedHistory.slice(0, 2).map((item) => <RecentlyCompletedRow key={item.engagementId} item={item} />)}
                </div>
              ) : (
                <p className={styles.sideEmpty}>Completed scenarios will appear here.</p>
              )}
            </section>

            {scenarioCatalogue?.items[0] && (
              <section className={styles.recommendationPanel}>
                <div>
                  <div className={styles.sectionEyebrow}>Recommended for you</div>
                  <h3>{scenarioCatalogue.items[0].title}</h3>
                  <p>Practise stakeholder discovery and commercial evidence gathering in a fresh industry context.</p>
                </div>
                <Button kind="secondary" size="sm" renderIcon={Renew} onClick={() => handleStart(scenarioCatalogue.items[0])}>
                  Start scenario
                </Button>
              </section>
            )}
          </aside>
        </div>
        )}

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

        <section id="scenario-catalogue" className={styles.catalogueSection} aria-labelledby="scenario-catalogue-heading">
          <div className={styles.sectionHeader}>
            <div>
              <div className={styles.sectionEyebrow}>Scenario catalogue</div>
              <h3 id="scenario-catalogue-heading">
                {stage === 'FIRST_VISIT' ? 'Other clients' : 'Find your next client'}
              </h3>
              <p>
                {stage === 'FIRST_VISIT'
                  ? 'Any of these works as a first engagement. They differ in industry and difficulty, not in what you have to do.'
                  : `Explore ${scenarioCatalogue?.totalElements.toLocaleString() ?? '...'} distinct, scenario-ready consulting engagements.`}
              </p>
            </div>
            <span className={styles.catalogueCount}>{scenarioCatalogue?.totalElements.toLocaleString() ?? 0} scenarios</span>
          </div>
          <div className={styles.catalogueControls}>
            <TextInput
              id="scenario-catalogue-search"
              labelText="Search scenarios"
              hideLabel
              placeholder="Search client, industry or opportunity"
              value={catalogueSearch}
              onChange={(event) => setCatalogueSearch(event.target.value)}
            />
            <Select id="scenario-catalogue-industry" labelText="Industry" hideLabel value={catalogueIndustry} onChange={(event) => setCatalogueIndustry(event.target.value)}>
              <SelectItem value="" text="All industries" />
              {catalogIndustries.map((industry) => <SelectItem key={industry} value={industry} text={industry} />)}
            </Select>
            <Select id="scenario-catalogue-difficulty" labelText="Difficulty" hideLabel value={String(catalogueDifficulty)} onChange={(event) => setCatalogueDifficulty(event.target.value ? Number(event.target.value) : '')}>
              <SelectItem value="" text="All difficulty" />
              <SelectItem value="2" text="Guided" />
              <SelectItem value="3" text="Standard" />
              <SelectItem value="4" text="Advanced" />
            </Select>
            <span className={styles.catalogueLoading}>{catalogueLoading ? 'Updating results...' : 'Cached catalogue'}</span>
          </div>
          {scenarioCatalogue?.items.length ? (
            <>
              <div className={styles.scenarioGrid}>
                {scenarioCatalogue.items.map((scenario) => (
                  <ScenarioCard key={scenario.id} scenario={scenario} onStart={() => handleStart(scenario)} isPending={startEngagement.isPending} />
                ))}
              </div>
              <Pagination
                className={styles.cataloguePagination}
                page={cataloguePage}
                pageSize={scenarioCatalogue.size}
                pageSizes={[9]}
                totalItems={scenarioCatalogue.totalElements}
                onChange={({ page }) => setCataloguePage(page)}
              />
            </>
          ) : (
            <div className={styles.emptyState}><Search size={20} /><span>No scenarios match these filters.</span></div>
          )}
        </section>
        </div>

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
