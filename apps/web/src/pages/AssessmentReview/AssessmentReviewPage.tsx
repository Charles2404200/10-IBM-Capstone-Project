import { useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { Grid, Column, Heading, Stack, Button, Tile, Tag, ProgressBar } from '@carbon/react'
import { useQueryClient } from '@tanstack/react-query'
import { useAssessment, useGenerateAssessment } from '@/api/hooks/useAssessment'
import { engagementKeys } from '@/api/hooks/useEngagements'
import { portfolioKeys } from '@/api/hooks/usePortfolio'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'

type OutcomePresentation = {
  label: string
  contractStatus: string
  tagType: 'green' | 'red' | 'blue' | 'purple'
}

function describeOutcome(outcome: string): OutcomePresentation {
  switch (outcome) {
    case 'PILOT_APPROVED':
      return { label: 'Pilot approved', contractStatus: 'Contract won', tagType: 'green' }
    case 'PROPOSAL_ACCEPTED':
    case 'WON':
      return { label: 'Proposal accepted', contractStatus: 'Contract won', tagType: 'green' }
    case 'STRATEGIC_PARTNERSHIP':
      return { label: 'Strategic partnership', contractStatus: 'Contract won', tagType: 'green' }
    case 'REVISION_REQUESTED':
      return { label: 'Revision requested', contractStatus: 'Client decision recorded', tagType: 'purple' }
    case 'FURTHER_DISCOVERY_REQUIRED':
      return { label: 'Further discovery requested', contractStatus: 'Client decision recorded', tagType: 'blue' }
    case 'DEFERRED':
      return { label: 'Decision deferred', contractStatus: 'Contract not awarded', tagType: 'blue' }
    case 'REJECTED':
    case 'PROPOSAL_REJECTED':
    case 'LOST':
      return { label: 'Proposal rejected', contractStatus: 'Contract not won', tagType: 'red' }
    default:
      return { label: outcome.replaceAll('_', ' '), contractStatus: 'Client decision recorded', tagType: 'blue' }
  }
}

function CompetencyBar({ name, score, evidenceNote }: { name: string; score: number; evidenceNote: string | null }) {
  return (
    <Tile>
      <Stack gap={2}>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <h5 style={{ color: '#161616' }}>{name}</h5>
          <span style={{ color: '#525252' }}>{score}/100</span>
        </div>
        <ProgressBar label="" hideLabel value={score} max={100} size="small" />
        {evidenceNote && <p style={{ color: '#525252', fontSize: '0.75rem' }}>{evidenceNote}</p>}
      </Stack>
    </Tile>
  )
}

export default function AssessmentReviewPage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const queryClient = useQueryClient()
  const { data: assessment, isLoading, isError, error } = useAssessment(engagementId!)
  const generateAssessment = useGenerateAssessment(engagementId!)

  const notFound = isError && (error as { response?: { status?: number } })?.response?.status === 404
  const generationError = generateAssessment.error as
    | { response?: { data?: { detail?: string } } }
    | null

  useEffect(() => {
    if (notFound && !generateAssessment.isPending && !generateAssessment.isSuccess) {
      generateAssessment.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notFound])

  useEffect(() => {
    if (!assessment && !generateAssessment.data) return
    void queryClient.invalidateQueries({ queryKey: engagementKeys.all })
    void queryClient.invalidateQueries({ queryKey: engagementKeys.detail(engagementId!) })
    void queryClient.invalidateQueries({ queryKey: portfolioKeys.summary })
  }, [assessment, engagementId, generateAssessment.data, queryClient])

  if (isLoading || generateAssessment.isPending) return <LoadingState description="Generating assessment…" />
  if (isError && !notFound) {
    const problem = error as { response?: { data?: { detail?: string } } }
    return <ErrorState title="Assessment unavailable" message={problem.response?.data?.detail ?? 'The assessment could not be loaded. Retry to recover the assessment for this engagement.'} actionLabel="Retry assessment" onAction={() => generateAssessment.mutate()} />
  }
  if (generateAssessment.isError) {
    return (
      <ErrorState
        title="Assessment could not be generated"
        message={generationError?.response?.data?.detail ?? 'Please retry after completing the proposal outcome.'}
        actionLabel="Retry assessment"
        onAction={() => generateAssessment.mutate()}
      />
    )
  }

  const result = assessment ?? generateAssessment.data
  if (!result) return <LoadingState description="Generating assessment…" />

  const outcome = describeOutcome(result.outcome)

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={6}>
          <div>
            <Heading>Engagement Assessment</Heading>
            <p style={{ color: '#525252', marginTop: '0.5rem' }}>
              Coaching feedback generated from your research, outreach, meeting and proposal.
            </p>
          </div>

          <Tile>
            <Stack gap={3}>
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <Tag type="blue" size="lg">
                  ENGAGEMENT COMPLETE
                </Tag>
                <Tag type={outcome.tagType} size="lg">
                  {outcome.label}
                </Tag>
                <span style={{ color: '#161616', fontSize: '1.5rem' }}>{result.overallScore}/100</span>
              </div>
              <p style={{ color: '#525252' }}><strong>{outcome.contractStatus}</strong></p>
              <p style={{ color: '#525252' }}>{result.feedbackSummary}</p>
            </Stack>
          </Tile>

          <Grid narrow>
            {result.competencyScores.map((c) => (
              <Column key={c.name} lg={8} md={4} sm={4} style={{ marginBottom: '1rem' }}>
                <CompetencyBar name={c.name} score={c.score} evidenceNote={c.evidenceNote} />
              </Column>
            ))}
          </Grid>

          <Grid narrow>
            <Column lg={8} md={4} sm={4}>
              <Tile>
                <Stack gap={2}>
                  <h5 style={{ color: '#161616' }}>Strengths</h5>
                  {result.strengths.map((s, i) => (
                    <p key={i} style={{ color: '#24a148' }}>✓ {s}</p>
                  ))}
                  {result.strengths.length === 0 && <p style={{ color: '#525252' }}>None recorded.</p>}
                </Stack>
              </Tile>
            </Column>
            <Column lg={8} md={4} sm={4}>
              <Tile>
                <Stack gap={2}>
                  <h5 style={{ color: '#161616' }}>Areas for Improvement</h5>
                  {result.improvementAreas.map((s, i) => (
                    <p key={i} style={{ color: '#f1c21b' }}>△ {s}</p>
                  ))}
                  {result.improvementAreas.length === 0 && <p style={{ color: '#525252' }}>None recorded.</p>}
                </Stack>
              </Tile>
            </Column>
          </Grid>

          <Button href="/dashboard" kind="secondary">
            Back to Command Centre
          </Button>
        </Stack>
      </Column>
    </Grid>
  )
}
