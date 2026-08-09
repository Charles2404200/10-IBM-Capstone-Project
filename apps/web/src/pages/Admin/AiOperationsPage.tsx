import { Button, Column, Grid, Heading, InlineNotification, InlineLoading, ProgressBar, Tag } from '@carbon/react'
import { Renew } from '@carbon/icons-react'
import { useAdminAiOperations } from '@/api/hooks/useAdminAiOperations'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import styles from './AdminOperationsPage.module.css'

export default function AiOperationsPage() {
  const operations = useAdminAiOperations()
  if (operations.isLoading) return <LoadingState />
  if (operations.isError) return <ErrorState />
  const data = operations.data
  if (!data) return <ErrorState />

  return <main className={styles.page}>
    <Grid condensed><Column lg={16} md={8} sm={4}>
      <header className={styles.header}><div><p className={styles.eyebrow}>AI reliability</p><Heading>AI operations</Heading><p>Live provider availability, quota consumption and deterministic task routing.</p></div><Button kind="tertiary" renderIcon={Renew} disabled={operations.isFetching} onClick={() => operations.refetch()}>Refresh</Button></header>
      {data.mockMode && <InlineNotification kind="warning" title="Simulation fallback mode is enabled" subtitle="Disable it before delivering production learning sessions." hideCloseButton />}
      {!data.mockMode && <InlineNotification kind={data.parallelEnabled ? "success" : "info"} title={data.parallelEnabled ? "Parallel model orchestration is active" : "Sequential provider routing is active"} subtitle={data.parallelEnabled ? `Up to ${data.parallelMaxCandidates} approved providers race per task; only schema-valid output can be selected.` : "Set AI_PARALLEL_ENABLED=true to enable concurrent validated provider execution."} hideCloseButton />}
      {operations.isFetching && <InlineLoading description="Refreshing AI operations" />}
      <section className={styles.providerGrid}>{data.providers.map((provider) => {
        const quotaPercent = provider.quotaLimit > 0 ? Math.round(provider.quotaUsed * 100 / provider.quotaLimit) : 0
        return <article className={styles.provider} key={provider.providerId}><div className={styles.providerHeading}><h2>{provider.providerId}</h2><Tag type={provider.available ? 'green' : 'red'}>{provider.available ? 'Available' : 'Unavailable'}</Tag></div><dl><div><dt>Circuit</dt><dd>{provider.circuitState}</dd></div><div><dt>Average latency</dt><dd>{provider.avgLatencyMs} ms</dd></div><div><dt>Requests today</dt><dd>{provider.requestsToday}</dd></div><div><dt>Failures</dt><dd>{provider.failureCount}</dd></div><div><dt>Fallback rate</dt><dd>{provider.fallbackRatePercent.toFixed(1)}%</dd></div></dl><ProgressBar label="Daily quota" value={quotaPercent} helperText={`${provider.quotaUsed} of ${provider.quotaLimit || 'unlimited'} requests`} /></article>
      })}</section>
      <section className={styles.routing}><h2>Task routing</h2><div className={styles.routingGrid}>{Object.entries(data.routing).map(([task, providers]) => <div key={task}><strong>{task.replaceAll('_', ' ')}</strong><span>{providers.join(' -> ') || 'No provider configured'}</span></div>)}</div></section>
    </Column></Grid>
  </main>
}
