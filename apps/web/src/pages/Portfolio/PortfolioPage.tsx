import { useMemo, useState } from 'react'
import {
  Grid,
  Column,
  Stack,
  Tile,
  Tag,
  Select,
  SelectItem,
  ProgressBar,
} from '@carbon/react'
import { TrophyFilled, Locked } from '@carbon/icons-react'
import { usePortfolioSummary, useReplayComparison } from '@/api/hooks/usePortfolio'
import { useMyAchievements } from '@/api/hooks/useAchievements'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { AchievementSummary, CompetencyTrend, CompletedEngagementView } from '@/api/types'
import PageHeader from '@/lifecycle/components/PageHeader'
import styles from './PortfolioPage.module.scss'
import { useAuthStore } from '@/store/authStore'

function StatTile({ label, value, accent }: { label: string; value: string | number; accent?: 'success' | 'danger'}) {
  return (
    <Tile className={styles.statTile}>
      <Stack gap={2}>
        <p className={styles.statLabel}>{label}</p>
        <span className={`${styles.statValue} ${
            accent === 'success' ? styles.statValueSuccess 
            : accent === 'danger' ? styles.statValueDanger 
            : ''
          }`}>{value}
        </span>
      </Stack>
    </Tile>
  )
}

/** Lightweight competency trend visualisation: one row per historical score,
 *  avoiding a chart-library dependency while still showing progression clearly. */
function CompetencyTrendCard({ trend }: { trend: CompetencyTrend }) {
  const latest = trend.points[trend.points.length - 1]
  const first = trend.points[0]
  const delta = trend.points.length > 1 ? latest.score - first.score : 0

  return (
    <Tile className={styles.trendTile}>
      <Stack gap={3}>
        <div className={styles.trendHeader}>
          <h5>{trend.competencyName}</h5>
          {trend.points.length > 1 && (
            <Tag type={delta >= 0 ? 'green' : 'red'} size="sm">
              {delta >= 0 ? '+' : ''}{delta} since first attempt
            </Tag>
          )}
        </div>
        <Stack gap={2}>
          {trend.points.map((p) => (
            <div key={p.engagementId} className={styles.trendRow}>
              <span className={styles.trendDate}>
                {new Date(p.generatedAt).toLocaleDateString()}
              </span>
              <div className={styles.trendProgress}>
                <ProgressBar label="" hideLabel value={p.score} max={100} size="small" />
              </div>
              <span className={styles.trendScore}>
                {p.score}
              </span>
            </div>
          ))}
        </Stack>
      </Stack>
    </Tile>
  )
}

function EngagementHistoryRow({ engagement }: { engagement: CompletedEngagementView }) {
  const won = engagement.outcome === 'PROPOSAL_ACCEPTED' || engagement.outcome === 'WON'

  return (
    <Tile className={styles.historyTile}>
      <Stack gap={2}>
        <div className={styles.historyHeader}>
          <div>
            <h5 className={styles.historyTitle}>{engagement.scenarioTitle}</h5>
            <Tag type={won ? 'green' : 'red'} size="sm">{engagement.outcome.replace(/_/g, ' ')}</Tag><div></div>
            <Tag type="cyan" size="sm">{engagement.industry}</Tag>
          </div>
          <div></div>
        </div>
        <div className={styles.historyMeta}>
          <span className={styles.historyDate}>
            {engagement.completedAt ? new Date(engagement.completedAt).toLocaleDateString() : 'In review'}
          </span>
          <span className={styles.historyScore}>{engagement.overallScore}/100</span>
        </div>
      </Stack>
    </Tile>
  )
}

function ReplayComparisonSection({ history }: { history: CompletedEngagementView[] }) {
  const [engagementA, setEngagementA] = useState('')
  const [engagementB, setEngagementB] = useState('')
  const { data: comparison, isFetching } = useReplayComparison(engagementA, engagementB)

  if (history.length < 2) return null

  return (
    <section className={styles.replaySection}>
      <h3 className={styles.sectionTitle}>Replay Comparison</h3>
      <Tile className={styles.replayTile}>
        <Stack gap={4}>
          <Grid narrow>
            <Column lg={8} md={4} sm={4}>
              <Select
                id="replay-a"
                labelText="Engagement A"
                value={engagementA}
                onChange={(e) => setEngagementA(e.target.value)}
              >
                <SelectItem value="" text="Select an engagement…" />
                {(history ?? []).map((h) => (
                  <SelectItem key={h.engagementId} value={h.engagementId} text={`${h.scenarioTitle} — ${h.overallScore}/100`} />
                ))}
              </Select>
            </Column>
            <Column lg={8} md={4} sm={4}>
              <Select
                id="replay-b"
                labelText="Engagement B"
                value={engagementB}
                onChange={(e) => setEngagementB(e.target.value)}
              >
                <SelectItem value="" text="Select an engagement…" />
                {(history ?? []).map((h) => (
                  <SelectItem key={h.engagementId} value={h.engagementId} text={`${h.scenarioTitle} — ${h.overallScore}/100`} />
                ))}
              </Select>
            </Column>
          </Grid>

          {isFetching && <LoadingState description="Loading comparison…" />}

          {comparison && (
            <Grid narrow>
              {[comparison.engagementA, comparison.engagementB].map((snapshot, idx) => (
                <Column key={idx} lg={8} md={4} sm={4}>
                  <Tile className={styles.replaySnapshot}>
                    <Stack gap={3}>
                      <div>
                        <h5 className={styles.snapshotTitle}>{snapshot.scenarioTitle}</h5>
                        <p className={styles.snapshotPersona}>vs. {snapshot.personaName}</p>
                      </div>
                      <span className={styles.snapshotScore}>{snapshot.overallScore}/100</span>
                      <div className={styles.competencyScoreList}>
                        {snapshot.competencyScores.map((c) => (
                          <div key={c.competencyName} className={styles.competencyScoreRow}>
                            <span className={styles.competencyScoreName}>{c.competencyName}</span>
                            <span className={styles.competencyScoreValue}>{c.score}</span>
                          </div>
                        ))}
                      </div>
                    </Stack>
                  </Tile>
                </Column>
                ),
              )}
            </Grid>
          )}
        </Stack>
      </Tile>
    </section>
  )
}

function AchievementBadge({ achievement }: { achievement: AchievementSummary }) {
  return (
    <Tile className={`${styles.achievementTile} ${ achievement.unlocked ? '' : styles.achievementLocked }`}>
      <Stack gap={3}>
        <div className={styles.achievementHeader}>
          {achievement.unlocked ? (
            <TrophyFilled size={20} className={styles.achievementIcon} />
          ) : (
            <Locked size={20} className={styles.achievementLockedIcon} />
          )}
          <h5 className={styles.achievementName}>{achievement.name}</h5>
        </div>
        <p className={styles.achievementDescription}>{achievement.description}</p>
        {achievement.unlocked ? (
          <Tag type="green" size="sm">
            Unlocked{' '}
            {achievement.unlockedAt
              ? new Date(achievement.unlockedAt).toLocaleDateString()
              : ''}
          </Tag>
        ) : (
          <div className={styles.achievementProgress}>
            <ProgressBar label="" hideLabel value={achievement.progressPercent} max={100} size="small" />
            <span className={styles.achievementProgressText}>{Math.round(achievement.progressPercent)}% complete</span>
          </div>
        )}
      </Stack>
    </Tile>
  )
}

function AchievementsSection() {
  const { data: achievements, isLoading } = useMyAchievements()

  if (isLoading || !achievements || achievements.length === 0) return null

  return (
    <section className={styles.section}>
      <h3 className={styles.sectionTitle}>Achievements</h3>
      <Grid narrow>
        {achievements.map((a) => (
          <Column key={a.id} lg={4} md={4} sm={4} className={styles.columnSpacing} >
            <AchievementBadge achievement={a} />
          </Column>
        ))}
      </Grid>
    </section>
  )
}

export default function PortfolioPage() {
  const { data: portfolio, isLoading, isError } = usePortfolioSummary()
  const { displayName } = useAuthStore()
  const sortedHistory = useMemo(
    () => (portfolio?.completedEngagementsHistory ?? []).slice().reverse(),
    [portfolio],
  )

  if (isLoading) return <LoadingState />
  if (isError || !portfolio) return <ErrorState />

  return (
    <>
    <PageHeader
      phase="COMPLETED"
      description={displayName
          ? `Welcome ${displayName}, see your competency growth and completed engagement history across every scenario.`
          : 'Your competency growth and completed engagement history across every scenario.'
      }
    />
    <Grid fullWidth className={styles.page}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={7}>
          <div></div>
          <Grid narrow>
            <Column lg={4} md={4} sm={4} className={styles.columnSpacing} >
              <StatTile label="Total Engagements" value={portfolio.totalEngagements} />
            </Column>
            <Column lg={4} md={4} sm={4} className={styles.columnSpacing} >
              <StatTile label="Contracts Won" value={portfolio.contractsWon} accent="success" />
            </Column>
            <Column lg={4} md={4} sm={4} className={styles.columnSpacing} >
              <StatTile label="Contracts Lost" value={portfolio.contractsLost} accent="danger" />
            </Column>
            <Column lg={4} md={4} sm={4} className={styles.columnSpacing} >
              <StatTile label="Average Score" value={portfolio.averageOverallScore || '—'} />
            </Column>
          </Grid>

          {portfolio.competencyTrends.length > 0 && (
            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>Competency Progression</h3>
              <Grid narrow>
                {portfolio.competencyTrends.map((trend) => (
                  <Column key={trend.competencyName} lg={8} md={4} sm={4} className={styles.columnSpacing} >
                    <CompetencyTrendCard trend={trend} />
                  </Column>
                ))}
              </Grid>
            </section>
          )}

          {sortedHistory.length > 0 ? (
            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>Completed Engagements</h3>
              <Grid narrow>
                {sortedHistory.map((h) => (
                  <Column key={h.engagementId} lg={5} md={4} sm={4} className={styles.columnSpacing} >
                    <EngagementHistoryRow engagement={h} />
                  </Column>
                ))}
              </Grid>
            </section>
          ) : (
            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>Completed Engagements</h3>
              <p className={styles.emptyState}>
                Complete your first engagement to start building your portfolio.
              </p>
            </section>
          )}

          <ReplayComparisonSection history={sortedHistory} />

          <AchievementsSection />
        </Stack>
      </Column>
    </Grid>
    </>
  )
}
