import { Button, InlineLoading, Tag } from '@carbon/react'
import {
  ArrowRight,
  ChartLine,
  CheckmarkFilled,
  DocumentAdd,
  Group,
  Idea,
  Launch,
  Renew,
  Settings,
  WarningAlt,
} from '@carbon/icons-react'
import { Link } from 'react-router-dom'
import { useAdminAiOperations } from '@/api/hooks/useAdminAiOperations'
import { useAdminPlatformOverview } from '@/api/hooks/useAdminPlatformOverview'
import { useAllScenariosForAdmin } from '@/api/hooks/useAdminScenarios'
import { useAuthStore } from '@/store/authStore'
import ErrorState from '@/components/shared/ErrorState'
import LoadingState from '@/components/shared/LoadingState'
import styles from './AdminConsolePage.module.css'

type ConsoleIcon = typeof Settings

function Metric({ icon: Icon, label, value, detail, tone = 'blue' }: {
  icon: ConsoleIcon
  label: string
  value: string | number
  detail: string
  tone?: 'blue' | 'green' | 'purple' | 'orange'
}) {
  return (
    <article className={`${styles.metric} ${styles[`metric${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
      <span className={styles.metricIcon}><Icon size={20} /></span>
      <div><span className={styles.metricLabel}>{label}</span><strong>{value}</strong><small>{detail}</small></div>
    </article>
  )
}

function ActionCard({ to, icon: Icon, title, detail, action, tone = 'blue' }: {
  to: string
  icon: ConsoleIcon
  title: string
  detail: string
  action: string
  tone?: 'blue' | 'green' | 'purple' | 'orange'
}) {
  return (
    <Link to={to} className={`${styles.actionCard} ${styles[`action${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
      <span className={styles.actionIcon}><Icon size={20} /></span>
      <div><h3>{title}</h3><p>{detail}</p></div>
      <span className={styles.actionLink}>{action} <ArrowRight size={16} /></span>
    </Link>
  )
}

function AttentionItem({ title, detail, to, kind = 'warning' }: {
  title: string
  detail: string
  to: string
  kind?: 'warning' | 'success'
}) {
  const Icon = kind === 'success' ? CheckmarkFilled : WarningAlt
  return <Link to={to} className={styles.attentionItem}>
    <Icon size={18} className={kind === 'success' ? styles.successIcon : styles.warningIcon} />
    <span><strong>{title}</strong><small>{detail}</small></span>
    <ArrowRight size={16} />
  </Link>
}

export default function AdminConsolePage() {
  const role = useAuthStore((state) => state.role)
  const canAdminister = role === 'ADMINISTRATOR'
  const canAuthorScenarios = role === 'SCENARIO_AUTHOR' || canAdminister
  const canViewAiOperations = role === 'REVIEWER' || canAdminister
  const scenarios = useAllScenariosForAdmin(canAuthorScenarios)
  const aiOperations = useAdminAiOperations(canViewAiOperations)
  const platform = useAdminPlatformOverview(canAdminister)

  const scenarioList = scenarios.data ?? []
  const activeScenarioCount = canAuthorScenarios
    ? scenarioList.filter((scenario) => scenario.status === 'ACTIVE').length
    : platform.data?.scenariosByStatus?.ACTIVE ?? 0
  const draftScenarioCount = scenarioList.filter((scenario) => scenario.status === 'DRAFT').length
  const healthyProviders = (aiOperations.data?.providers ?? []).filter((provider) => provider.available).length
  const unavailableProviders = (aiOperations.data?.providers ?? []).filter((provider) => !provider.available).length
  const scenarioStates = platform.data?.scenariosByStatus ?? {}
  const platformScenarioCount = Object.values(scenarioStates).reduce((sum, count) => sum + count, 0)
  const isRefreshing = scenarios.isFetching || aiOperations.isFetching || platform.isFetching

  const scenarioTotal = canAuthorScenarios && scenarios.isLoading ? '...' : platformScenarioCount || scenarioList.length
  const activeScenarios = canAuthorScenarios && scenarios.isLoading ? '...' : activeScenarioCount
  const activeRuns = platform.isLoading ? '...' : platform.data?.activeEngagements ?? 0
  const completionRate = platform.isLoading ? '...' : `${platform.data?.completionRatePercent ?? 0}%`

  const isError = (canAuthorScenarios && scenarios.isError) || (canViewAiOperations && aiOperations.isError) || (canAdminister && platform.isError)
  if (isError) {
    return (
      <ErrorState
        actionLabel="Retry"
        onAction={() => {
          if (canAuthorScenarios) void scenarios.refetch()
          if (canViewAiOperations) void aiOperations.refetch()
          if (canAdminister) void platform.refetch()
        }}
      />
    )
  }

  const isLoading = (canAuthorScenarios && scenarios.isLoading) || (canViewAiOperations && aiOperations.isLoading) || (canAdminister && platform.isLoading)
  if (isLoading) return <LoadingState />

  return (
    <main className={styles.console}>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>Platform administration</p>
          <h1>Admin Console</h1>
          <p>Keep learning journeys ready, give people the right access, and monitor AI delivery from one calm workspace.</p>
        </div>
        <div className={styles.headerActions}>
          <Button kind="ghost" size="sm" renderIcon={Renew} disabled={isRefreshing} onClick={() => {
            void scenarios.refetch()
            void aiOperations.refetch()
            void platform.refetch()
          }}>Refresh</Button>
          {canAuthorScenarios && <Button as={Link} to="/dashboard/admin/scenarios" size="sm" renderIcon={DocumentAdd}>Create scenario</Button>}
        </div>
      </header>

      <section className={styles.metrics} aria-label="Platform summary">
        <Metric icon={Settings} label="Learning journeys" value={activeScenarios} detail={`${scenarioTotal} total scenarios`} tone="blue" />
        {canAdminister && <Metric icon={Group} label="Learners in progress" value={activeRuns} detail={`${platform.data?.totalEngagements ?? 0} learner runs`} tone="purple" />}
        {canViewAiOperations && <Metric icon={ChartLine} label="AI services ready" value={aiOperations.isLoading ? '...' : healthyProviders} detail={aiOperations.data?.mockMode ? 'Fallback mode is enabled' : 'Live provider routing'} tone="green" />}
        {canAdminister && <Metric icon={Idea} label="Learning completed" value={completionRate} detail={platform.data?.averageAssessmentScore == null ? 'No assessment score yet' : `Average assessment ${platform.data.averageAssessmentScore}/100`} tone="orange" />}
      </section>

      <section className={styles.canvas} aria-label="Administrative workspace">
        <section className={styles.primaryPanel}>
          <div className={styles.panelHeading}>
            <div><p className={styles.panelEyebrow}>Start here</p><h2>What would you like to manage?</h2></div>
            <span className={styles.liveStatus}><i /> Live updates</span>
          </div>

          <div className={styles.actionGrid}>
            {canAuthorScenarios && <ActionCard to="/dashboard/admin/scenarios" icon={Settings} title="Learning journeys" detail="Create scenarios, define people, facts and learning rules." action="Open scenarios" tone="blue" />}
            {canAdminister && <ActionCard to="/dashboard/admin/users" icon={Group} title="People and access" detail="Give learners, reviewers and authors the right level of access." action="Manage people" tone="green" />}
            {canAdminister && <ActionCard to="/dashboard/admin/achievements" icon={Idea} title="Progression and badges" detail="Recognise strong consulting behaviours across scenarios." action="Manage progression" tone="purple" />}
            {canViewAiOperations && <ActionCard to="/dashboard/admin/ai-operations" icon={ChartLine} title="AI delivery" detail="Review provider availability, capacity and approved routing." action="View AI health" tone="orange" />}
          </div>

          {canAdminister && <section className={styles.activity}>
            <div className={styles.subsectionHeading}>
              <div><h2>Learning activity</h2><p>Most-used scenarios based on persisted learner runs.</p></div>
              <Link to="/dashboard/admin/scenarios">View all scenarios <Launch size={14} /></Link>
            </div>
            {platform.data?.scenarios.length ? (
              <div className={styles.activityList}>
                {platform.data.scenarios.slice(0, 3).map((scenario) => (
                  <div className={styles.activityRow} key={scenario.scenarioId}>
                    <div><strong>{scenario.title}</strong><small>{scenario.engagementCount} learner runs</small></div>
                    <span><b>{scenario.completedCount}</b> completed</span>
                    <span><b>{scenario.averageAssessmentScore == null ? '—' : `${scenario.averageAssessmentScore}`}</b> avg. score</span>
                  </div>
                ))}
              </div>
            ) : <p className={styles.emptyActivity}>Learning activity will appear when learners begin a scenario.</p>}
          </section>}
        </section>

        <aside className={styles.sidePanel}>
          <section className={styles.attentionPanel}>
            <div className={styles.panelHeading}><div><p className={styles.panelEyebrow}>Readiness</p><h2>Needs your attention</h2></div><Tag type={draftScenarioCount || unavailableProviders || aiOperations.data?.mockMode ? 'warm-gray' : 'green'}>{draftScenarioCount || unavailableProviders || aiOperations.data?.mockMode ? 'Review' : 'All clear'}</Tag></div>
            <div className={styles.attentionList}>
              {canAuthorScenarios && draftScenarioCount > 0 && <AttentionItem to="/dashboard/admin/scenarios" title={`${draftScenarioCount} draft ${draftScenarioCount === 1 ? 'scenario' : 'scenarios'}`} detail="Finish and publish when each journey is ready for learners." />}
              {canViewAiOperations && aiOperations.data?.mockMode && <AttentionItem to="/dashboard/admin/ai-operations" title="AI fallback mode is on" detail="Review live provider configuration before production sessions." />}
              {canViewAiOperations && unavailableProviders > 0 && <AttentionItem to="/dashboard/admin/ai-operations" title={`${unavailableProviders} AI ${unavailableProviders === 1 ? 'service needs' : 'services need'} review`} detail="A healthy provider can still handle the affected task through approved fallback routing." />}
              {!draftScenarioCount && !unavailableProviders && !aiOperations.data?.mockMode && <AttentionItem kind="success" to="/dashboard/admin/scenarios" title="Your platform is ready" detail="Scenarios and AI delivery are ready for the next learner session." />}
            </div>
          </section>

          <section className={styles.servicePanel}>
            <div className={styles.subsectionHeading}><div><h2>Service pulse</h2><p>Current delivery signals.</p></div><Link to="/dashboard/admin/ai-operations">Details <ArrowRight size={14} /></Link></div>
            <dl>
              <div><dt>AI routing</dt><dd>{aiOperations.data?.parallelEnabled ? `Parallel, up to ${aiOperations.data.parallelMaxCandidates}` : 'Validated provider routing'}</dd></div>
              <div><dt>Service availability</dt><dd>{aiOperations.isLoading ? 'Checking' : `${healthyProviders} provider${healthyProviders === 1 ? '' : 's'} ready`}</dd></div>
              <div><dt>Scenario catalogue</dt><dd>{activeScenarioCount} available to learners</dd></div>
            </dl>
          </section>
        </aside>
      </section>

      {isRefreshing && <div className={styles.refreshing}><InlineLoading description="Refreshing administration data" /></div>}
    </main>
  )
}
