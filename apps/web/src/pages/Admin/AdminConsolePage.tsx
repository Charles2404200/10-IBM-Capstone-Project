import { Button, Column, Grid, InlineLoading, Tag, Tile } from '@carbon/react'
import { ChartLine, DocumentAdd, Group, Idea, Launch, Settings } from '@carbon/icons-react'
import { Link } from 'react-router-dom'
import { useAllScenariosForAdmin } from '@/api/hooks/useAdminScenarios'
import { useAdminAiOperations } from '@/api/hooks/useAdminAiOperations'
import { useAdminPlatformOverview } from '@/api/hooks/useAdminPlatformOverview'
import { useAuthStore } from '@/store/authStore'
import styles from './AdminConsolePage.module.css'

function Metric({ label, value, detail }: { label: string; value: string | number; detail: string }) {
  return <Tile className={styles.metric}><span>{label}</span><strong>{value}</strong><small>{detail}</small></Tile>
}

function ConsoleLink({ to, icon: Icon, title, detail, action }: {
  to: string; icon: typeof Settings; title: string; detail: string; action: string
}) {
  return (
    <Link to={to} className={styles.consoleLink}>
      <Icon size={24} />
      <div><h3>{title}</h3><p>{detail}</p></div>
      <span>{action}<Launch size={16} /></span>
    </Link>
  )
}

export default function AdminConsolePage() {
  const role = useAuthStore((state) => state.role)
  const scenarios = useAllScenariosForAdmin()
  const canAdminister = role === 'ADMINISTRATOR'
  const canViewAiOperations = role === 'REVIEWER' || canAdminister
  const aiOperations = useAdminAiOperations(canViewAiOperations)
  const platform = useAdminPlatformOverview(canAdminister)

  const scenarioList = scenarios.data ?? []
  const activeScenarioCount = scenarioList.filter((scenario) => scenario.status === 'ACTIVE').length
  const healthyProviders = (aiOperations.data?.providers ?? []).filter((provider) => provider.available).length

  return (
    <main className={styles.console}>
      <header className={styles.header}>
        <div><p className={styles.eyebrow}>Platform administration</p><h1>Admin Console</h1><p>Manage the controlled simulation, people and AI operations from one place.</p></div>
        <Button as={Link} to="/dashboard/admin/scenarios" renderIcon={DocumentAdd}>Create scenario</Button>
      </header>

      <section className={styles.metrics} aria-label="Platform summary">
        <Metric label="Active scenarios" value={scenarios.isLoading ? '...' : activeScenarioCount} detail={`${scenarioList.length} total scenarios`} />
        {canAdminister && <Metric label="Active engagements" value={platform.isLoading ? '...' : platform.data?.activeEngagements ?? 0} detail={`${platform.data?.totalEngagements ?? 0} total learner runs`} />}
        {canViewAiOperations && <Metric label="Live AI providers" value={aiOperations.isLoading ? '...' : healthyProviders} detail={aiOperations.data?.mockMode ? 'Simulation fallback enabled' : 'Production routing enabled'} />}
        {canAdminister && <Metric label="Completion rate" value={platform.isLoading ? '...' : `${platform.data?.completionRatePercent ?? 0}%`} detail={platform.data?.averageAssessmentScore == null ? 'No completed assessments' : `Average score ${platform.data.averageAssessmentScore}/100`} />}
      </section>

      <Grid condensed className={styles.workspace}>
        <Column lg={10} md={8} sm={4}>
          <section className={styles.section}>
            <div className={styles.sectionHeading}><div><h2>Simulation workspace</h2><p>Author and govern reusable learning scenarios.</p></div></div>
            <div className={styles.linkList}>
              <ConsoleLink to="/dashboard/admin/scenarios" icon={Settings} title="Scenario management" detail="Scenarios, personas, rubrics, knowledge and gameplay rules." action="Manage scenarios" />
              {canAdminister && <ConsoleLink to="/dashboard/admin/achievements" icon={Idea} title="Progression design" detail="Achievement rules that recognise evidence-based learner behaviours." action="Manage achievements" />}
            </div>
          </section>
        </Column>
        <Column lg={6} md={8} sm={4}>
          <section className={styles.section}>
            <div className={styles.sectionHeading}><div><h2>Operations</h2><p>Enterprise controls and service health.</p></div></div>
            <div className={styles.linkList}>
              {canAdminister && <ConsoleLink to="/dashboard/admin/users" icon={Group} title="People and access" detail="Roles and account access." action="Manage users" />}
              {canViewAiOperations && <ConsoleLink to="/dashboard/admin/ai-operations" icon={ChartLine} title="AI operations" detail="Provider health, quota and task routing." action="View operations" />}
            </div>
          </section>
        </Column>
      </Grid>

      {canAdminister && platform.data && <section className={styles.activity}>
        <div className={styles.sectionHeading}><div><h2>Learning activity</h2><p>Completion and assessment data calculated from persisted engagements.</p></div></div>
        <div className={styles.activityTable}><div className={styles.activityHeader}><span>Scenario</span><span>Runs</span><span>Completed</span><span>Average score</span></div>{platform.data.scenarios.slice(0, 5).map((scenario) => <div className={styles.activityRow} key={scenario.scenarioId}><strong>{scenario.title}</strong><span>{scenario.engagementCount}</span><span>{scenario.completedCount}</span><span>{scenario.averageAssessmentScore == null ? 'Not available' : `${scenario.averageAssessmentScore}/100`}</span></div>)}</div>
      </section>}

      {(scenarios.isFetching || aiOperations.isFetching || platform.isFetching) && <div className={styles.refreshing}><InlineLoading description="Refreshing platform status" /></div>}
      {canAdminister && aiOperations.data?.mockMode && <div className={styles.warning}><Tag type="red">Attention</Tag><span>AI operations report simulation fallback mode. Review deployment configuration before running learner sessions.</span></div>}
    </main>
  )
}
