import { useMemo, useState } from 'react'
import { Grid, Column, Stack, Tile, Tag, Select, SelectItem, ProgressBar, Button, Checkbox } from '@carbon/react'
import { TrophyFilled, Locked } from '@carbon/icons-react'
import { usePortfolioSummary, useReplayComparison } from '@/api/hooks/usePortfolio'
import { useMyAchievements } from '@/api/hooks/useAchievements'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { AchievementSummary, CompetencyTrend, CompletedEngagementView } from '@/api/types'
import PageHeader from '@/lifecycle/components/PageHeader'
import styles from './PortfolioPage.module.scss'
import { useAuthStore } from '@/store/authStore'

function StatTile({
  label,
  value,
  accent,
  helper,
}: {
  label: string
  value: string | number
  accent?: 'success' | 'danger'
  helper?: string
}) {
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
        {helper && <p className={styles.statHelper}>{helper}</p>}
      </Stack>
    </Tile>
  )
}

/** Lightweight competency trend visualisation: one row per historical score,
 *  avoiding a chart-library dependency while still showing progression clearly. */
function CompetencyTrendCard({ trend, showHistory }: { trend: CompetencyTrend, showHistory: boolean }) {
  const orderedPoints = [...trend.points].sort((a, b) =>
    new Date(a.generatedAt).getTime() - new Date(b.generatedAt).getTime()
  )
  const latest = orderedPoints[orderedPoints.length - 1]
  const first = orderedPoints[0]
  const delta = orderedPoints.length > 1 ? latest.score - first.score : 0
  const visiblePoints = showHistory ? orderedPoints : [latest]
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
          {visiblePoints.map((p) => (
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

const colors = ['#0f62fe', '#24a148', '#1192e8', '#da1e28']

function CompetencyGraphLegend({ trends, hiddenCompetencies, toggleCompetency } : { trends: CompetencyTrend[], hiddenCompetencies: Set<string>, toggleCompetency: (competencyName: string) => void}) {
  return (
    <div className={styles.combinedGraphLegend}>
      {trends.map((trend, index) => {
        const isHidden = hiddenCompetencies.has(trend.competencyName)

        return (
          <div key={trend.competencyName} className={styles.combinedGraphLegendItem} style={{ '--competency-color': colors[index % colors.length] } as React.CSSProperties} >
            <Checkbox
              id={`competency-${index}`}
              labelText={trend.competencyName}
              checked={!isHidden}
              onChange={() => toggleCompetency(trend.competencyName)}
            />
          </div>
        )
      })}
    </div>
  )
}

// responsive graph to show progress over attempts
function CompetencyTrendGraph({ trends }: { trends: CompetencyTrend[] }) {
  const [hoveredCompetency, setHoveredCompetency] = useState<string | null>(null)
  const [hiddenCompetencies, setHiddenCompetencies] = useState<Set<string>>(new Set())

  const chartData = useMemo(() => {
    const pointsByEngagement = new Map<string, {
      engagementId: string
      generatedAt: string
      [key: string]: string | number
    }>()

    trends.forEach((trend, trendIndex) => {
      trend.points.forEach((point) => {
        if (!pointsByEngagement.has(point.engagementId)) {
          pointsByEngagement.set(point.engagementId, {
            engagementId: point.engagementId,
            generatedAt: point.generatedAt,
          })
        }

        const existing = pointsByEngagement.get(point.engagementId)

        if (existing) {
          existing[`competency_${trendIndex}`] = point.score
        }
      })
    })

    return Array.from(pointsByEngagement.values())
      .sort((a, b) => new Date(a.generatedAt).getTime() - new Date(b.generatedAt).getTime())
      .map((point, index) => ({
        ...point,
        attempt: `Attempt ${index + 1}`,
      }))
  }, [trends])

  const toggleCompetency = (competencyName: string) => {
    setHiddenCompetencies((current) => {
      const next = new Set(current)

      if (next.has(competencyName)) {
        next.delete(competencyName)
      } else {
        next.add(competencyName)
      }

      return next
    })
  }

  if (trends.length === 0 || chartData.length === 0) return null

  return (
    <Tile className={styles.combinedTrendTile}>
      <Stack gap={4}>
        <div>
          <h5 className={styles.combinedTrendTitle}>Progress Across Attempts</h5>
          <p className={styles.combinedTrendDescription}>Track how each competency has changed across your completed engagements.</p>
        </div>
        <div className={styles.combinedTrendChart}>
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
              <XAxis dataKey="attempt" tick={{ fill: '#525252', fontSize: 12 }} axisLine={{ stroke: '#8d8d8d' }} tickLine={{ stroke: '#8d8d8d' }} />
              <YAxis domain={[0, 100]} tick={{ fill: '#525252', fontSize: 12 }} axisLine={{ stroke: '#8d8d8d' }} tickLine={{ stroke: '#8d8d8d' }} />
              <Tooltip
                formatter={(value, _name, item) => {
                  const trendIndex = Number(String(item.dataKey).replace('competency_', ''))
                  return [ value, trends[trendIndex]?.competencyName ?? 'Competency']
                }}
                labelFormatter={(label) => label}
              />
              {trends.map((trend, index) => {
                const isHidden = hiddenCompetencies.has(trend.competencyName)
                const isDimmed = hoveredCompetency !== null && hoveredCompetency !== trend.competencyName

                return (
                  <Line
                    key={ trend.competencyName }
                    type="monotone"
                    dataKey={ `competency_${index}` }
                    name={ trend.competencyName }
                    stroke={ colors[index % colors.length] }
                    strokeWidth={ isDimmed ? 0.5 : 1.75 }
                    strokeOpacity={ isDimmed ? 0.25 : 1 }
                    dot={{ r: isDimmed ? 1 : 2.5 }}
                    activeDot={{ r: isDimmed ? 2 : 5 }}
                    hide={ isHidden }
                    onMouseEnter={() => { setHoveredCompetency(trend.competencyName) }}
                    onMouseLeave={() => { setHoveredCompetency(null) }}
                  />
                )
              })}
            </LineChart>
          </ResponsiveContainer>
        </div>
        <CompetencyGraphLegend
          trends={trends}
          hiddenCompetencies={hiddenCompetencies}
          toggleCompetency={toggleCompetency}
        />
      </Stack>
    </Tile>
  )
}

function EngagementHistoryRow({ engagement }: { engagement: CompletedEngagementView }) {
  const won = ['PILOT_APPROVED', 'PROPOSAL_ACCEPTED', 'STRATEGIC_PARTNERSHIP', 'WON']
    .includes(engagement.outcome)
  const rejected = ['REJECTED', 'PROPOSAL_REJECTED', 'LOST'].includes(engagement.outcome)
  return (
    <Tile className={styles.historyTile}>
      <Stack gap={2}>
        <div>
          <h5 className={styles.historyTitle}>{engagement.scenarioTitle}</h5>
          <div className={styles.historyTags}>
            <Tag type={won ? 'green' : rejected ? 'red' : 'purple'} size="sm">{engagement.outcome.replace(/_/g, ' ')}</Tag>
            <Tag type="cyan" size="sm">{engagement.industry}</Tag>
          </div>
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
  const [showCompetencyHistory, setShowCompetencyHistory] = useState(false)
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
          <Grid narrow>
            <Column lg={4} md={4} sm={4} className={styles.columnSpacing}>
              <StatTile
                label="Completed engagements"
                value={`${portfolio.completedEngagements} / ${portfolio.totalEngagements}`}
                helper={`${portfolio.totalEngagements - portfolio.completedEngagements} still in progress`}
              />
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
              {portfolio.competencyTrends.some((trend) => trend.points.length > 1) && (
                <Button kind="ghost" size="sm" className={styles.historyButton} onClick={() => setShowCompetencyHistory((current) => !current)} >
                  {showCompetencyHistory ? 'Hide history' : 'View history'}
                </Button>
              )}
              <Grid narrow>
                {portfolio.competencyTrends.map((trend) => (
                  <Column key={trend.competencyName} lg={8} md={4} sm={4} className={styles.columnSpacing} >
                    <CompetencyTrendCard trend={trend} showHistory={showCompetencyHistory} />
                  </Column>
                ))}
              </Grid>
              <CompetencyTrendGraph trends={portfolio.competencyTrends} />
            </section>
          )}

          {sortedHistory.length > 0 ? (
            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>
                Completed Engagements ({portfolio.completedEngagements} of {portfolio.totalEngagements})
              </h3>
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
