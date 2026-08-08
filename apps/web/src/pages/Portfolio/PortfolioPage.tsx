import { useMemo, useState } from 'react'
import {
  Grid,
  Column,
  Heading,
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

function StatTile({ label, value, accent }: { label: string; value: string | number; accent?: string }) {
  return (
    <Tile>
      <Stack gap={2}>
        <p style={{ color: '#525252', fontSize: '0.75rem', textTransform: 'uppercase' }}>{label}</p>
        <span style={{ color: accent ?? '#161616', fontSize: '2rem', fontWeight: 600 }}>{value}</span>
      </Stack>
    </Tile>
  )
}

/** Lightweight competency trend visualisation: one row per historical score,
 *  avoiding a chart-library dependency while still showing progression clearly. */
function CompetencyTrendCard({ trend }: { trend: CompetencyTrend }) {
  const latest = trend.points[trend.points.length - 1]
  const first = trend.points[0];
  const delta = trend.points.length > 1 ? latest.score - first.score : 0

  return (
    <Tile>
      <Stack gap={3}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h5 style={{ color: '#161616' }}>{trend.competencyName}</h5>
          {trend.points.length > 1 && (
            <Tag type={delta >= 0 ? 'green' : 'red'} size="sm">
              {delta >= 0 ? '+' : ''}{delta} since first attempt
            </Tag>
          )}
        </div>
        <Stack gap={2}>
          {trend.points.map((p) => (
            <div key={p.engagementId} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <span style={{ color: '#525252', fontSize: '0.75rem', width: '6.5rem', flexShrink: 0 }}>
                {new Date(p.generatedAt).toLocaleDateString()}
              </span>
              <div style={{ flex: 1 }}>
                <ProgressBar label="" hideLabel value={p.score} max={100} size="small" />
              </div>
              <span style={{ color: '#525252', fontSize: '0.75rem', width: '2.5rem', textAlign: 'right' }}>
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
  const won = engagement.outcome === 'CONTRACT_WON' || engagement.outcome === 'WON'
  return (
    <Tile>
      <Stack gap={2}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h5 style={{ color: '#161616' }}>{engagement.scenarioTitle}</h5>
            <Tag type="cyan" size="sm">{engagement.industry}</Tag>
          </div>
          <Tag type={won ? 'green' : 'red'}>{engagement.outcome.replace(/_/g, ' ')}</Tag>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ color: '#525252', fontSize: '0.75rem' }}>
            {engagement.completedAt ? new Date(engagement.completedAt).toLocaleDateString() : 'In review'}
          </span>
          <span style={{ color: '#161616', fontWeight: 600 }}>{engagement.overallScore}/100</span>
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
    <section>
      <h3 style={{ marginBottom: '1rem', color: '#161616' }}>Replay Comparison</h3>
      <Tile>
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
                  <Tile>
                    <Stack gap={3}>
                      <div>
                        <h5 style={{ color: '#161616' }}>{snapshot.scenarioTitle}</h5>
                        <p style={{ color: '#525252', fontSize: '0.75rem' }}>vs. {snapshot.personaName}</p>
                      </div>
                      <span style={{ color: '#161616', fontSize: '1.5rem', fontWeight: 600 }}>
                        {snapshot.overallScore}/100
                      </span>
                      <Stack gap={2}>
                        {snapshot.competencyScores.map((c) => (
                          <div key={c.competencyName} style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <span style={{ color: '#525252', fontSize: '0.875rem' }}>{c.competencyName}</span>
                            <span style={{ color: '#161616', fontSize: '0.875rem' }}>{c.score}</span>
                          </div>
                        ))}
                      </Stack>
                    </Stack>
                  </Tile>
                </Column>
              ))}
            </Grid>
          )}
        </Stack>
      </Tile>
    </section>
  )
}

function AchievementBadge({ achievement }: { achievement: AchievementSummary }) {
  return (
    <Tile style={{ opacity: achievement.unlocked ? 1 : 0.6 }}>
      <Stack gap={3}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          {achievement.unlocked ? (
            <TrophyFilled size={20} style={{ color: '#f1c21b' }} />
          ) : (
            <Locked size={20} style={{ color: '#525252' }} />
          )}
          <h5 style={{ color: '#161616' }}>{achievement.name}</h5>
        </div>
        <p style={{ color: '#525252', fontSize: '0.8rem' }}>{achievement.description}</p>
        {achievement.unlocked ? (
          <Tag type="green" size="sm">
            Unlocked {achievement.unlockedAt ? new Date(achievement.unlockedAt).toLocaleDateString() : ''}
          </Tag>
        ) : (
          <div>
            <ProgressBar label="" hideLabel value={achievement.progressPercent} max={100} size="small" />
            <span style={{ color: '#525252', fontSize: '0.75rem' }}>{Math.round(achievement.progressPercent)}% complete</span>
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
    <section>
      <h3 style={{ marginBottom: '1rem', color: '#161616' }}>Achievements</h3>
      <Grid narrow>
        {achievements.map((a) => (
          <Column key={a.id} lg={4} md={4} sm={4} style={{ marginBottom: '1rem' }}>
            <AchievementBadge achievement={a} />
          </Column>
        ))}
      </Grid>
    </section>
  )
}

export default function PortfolioPage() {
  const { data: portfolio, isLoading, isError } = usePortfolioSummary()

  const sortedHistory = useMemo(
    () => (portfolio?.completedEngagementsHistory ?? []).slice().reverse(),
    [portfolio],
  )

  if (isLoading) return <LoadingState />
  if (isError || !portfolio) return <ErrorState />

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={7}>
          <div>
            <Heading>Portfolio &amp; Progression</Heading>
            <p style={{ color: '#525252', marginTop: '0.5rem' }}>
              Your competency growth and completed engagement history across every scenario.
            </p>
          </div>

          <Grid narrow>
            <Column lg={4} md={4} sm={4} style={{ marginBottom: '1rem' }}>
              <StatTile label="Total Engagements" value={portfolio.totalEngagements} />
            </Column>
            <Column lg={4} md={4} sm={4} style={{ marginBottom: '1rem' }}>
              <StatTile label="Contracts Won" value={portfolio.contractsWon} accent="#24a148" />
            </Column>
            <Column lg={4} md={4} sm={4} style={{ marginBottom: '1rem' }}>
              <StatTile label="Contracts Lost" value={portfolio.contractsLost} accent="#da1e28" />
            </Column>
            <Column lg={4} md={4} sm={4} style={{ marginBottom: '1rem' }}>
              <StatTile label="Average Score" value={portfolio.averageOverallScore || '—'} />
            </Column>
          </Grid>

          {portfolio.competencyTrends.length > 0 && (
            <section>
              <h3 style={{ marginBottom: '1rem', color: '#161616' }}>Competency Progression</h3>
              <Grid narrow>
                {portfolio.competencyTrends.map((trend) => (
                  <Column key={trend.competencyName} lg={8} md={4} sm={4} style={{ marginBottom: '1rem' }}>
                    <CompetencyTrendCard trend={trend} />
                  </Column>
                ))}
              </Grid>
            </section>
          )}

          {sortedHistory.length > 0 ? (
            <section>
              <h3 style={{ marginBottom: '1rem', color: '#161616' }}>Completed Engagements</h3>
              <Grid narrow>
                {sortedHistory.map((h) => (
                  <Column key={h.engagementId} lg={5} md={4} sm={4} style={{ marginBottom: '1rem' }}>
                    <EngagementHistoryRow engagement={h} />
                  </Column>
                ))}
              </Grid>
            </section>
          ) : (
            <Tile>
              <p style={{ color: '#525252' }}>
                Complete your first engagement to start building your portfolio.
              </p>
            </Tile>
          )}

          <ReplayComparisonSection history={sortedHistory} />

          <AchievementsSection />
        </Stack>
      </Column>
    </Grid>
  )
}
