import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Button,
  Tag,
  Tile,
  TextInput,
  TextArea,
  InlineNotification,
} from '@carbon/react'
import { ArrowRight, Send } from '@carbon/icons-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useOutreach, useSendOutreach } from '@/api/hooks/useOutreach'
import LoadingState from '@/components/shared/LoadingState'
import type { OutreachAttempt } from '@/api/types'

const schema = z.object({
  subject: z.string().min(5, 'Subject required').max(200),
  body: z.string().min(50, 'Message must be at least 50 characters').max(5000),
})

type FormValues = z.infer<typeof schema>

const OUTCOME_TAG: Record<string, 'green' | 'magenta' | 'red' | 'gray'> = {
  ACCEPTED: 'green',
  FOLLOW_UP_REQUIRED: 'magenta',
  REJECTED: 'red',
  PENDING: 'gray',
}

function ScoreBar({ label, value }: { label: string; value: number | null }) {
  if (value === null) return null
  return (
    <div>
      <p style={{ color: '#525252', fontSize: '0.75rem', marginBottom: '0.25rem' }}>{label}</p>
      <div style={{ background: '#e0e0e0', height: '4px', borderRadius: '2px' }}>
        <div
          style={{
            background: value >= 70 ? '#24a148' : value >= 40 ? '#f1c21b' : '#da1e28',
            width: `${value}%`,
            height: '100%',
            borderRadius: '2px',
          }}
        />
      </div>
    </div>
  )
}

function AttemptThread({ attempt }: { attempt: OutreachAttempt }) {
  return (
    <Tile style={{ marginBottom: '1rem' }}>
      <Stack gap={3}>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Tag type="blue" size="sm">Attempt #{attempt.attemptNumber}</Tag>
          <Tag type={OUTCOME_TAG[attempt.outcome]} size="sm">{attempt.outcome}</Tag>
        </div>
        <div>
          <p style={{ color: '#525252', fontSize: '0.75rem' }}>Subject</p>
          <p style={{ color: '#161616' }}>{attempt.subject}</p>
        </div>
        <div>
          <p style={{ color: '#525252', fontSize: '0.75rem' }}>Your message</p>
          <p style={{ color: '#525252', whiteSpace: 'pre-wrap' }}>{attempt.body}</p>
        </div>
        {attempt.clientReply && (
          <div style={{ borderLeft: '3px solid #0f62fe', paddingLeft: '1rem' }}>
            <p style={{ color: '#525252', fontSize: '0.75rem' }}>Client reply</p>
            <p style={{ color: '#525252' }}>{attempt.clientReply}</p>
          </div>
        )}
        <Stack gap={2}>
          <ScoreBar label="Personalisation" value={attempt.scorePersonalisation} />
          <ScoreBar label="Relevance" value={attempt.scoreRelevance} />
          <ScoreBar label="Clarity" value={attempt.scoreClarity} />
          <ScoreBar label="Call to Action" value={attempt.scoreCallToAction} />
        </Stack>
      </Stack>
    </Tile>
  )
}

export default function OutreachWorkspacePage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()
  const { data: attempts, isLoading } = useOutreach(engagementId!)
  const sendOutreach = useSendOutreach(engagementId!)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = (data: FormValues) => {
    sendOutreach.mutate(data, { onSuccess: () => reset() })
  }

  if (isLoading) return <LoadingState />

  const lastAttempt = attempts?.[attempts.length - 1]
  const meetingSecured = lastAttempt?.outcome === 'ACCEPTED'

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={10} md={5} sm={4}>
        <Stack gap={6}>
          <div>
            <Heading>Outreach Workspace</Heading>
            <p style={{ color: '#525252', marginTop: '0.5rem' }}>
              Compose your cold outreach. Use your research to personalise.
            </p>
          </div>

          {meetingSecured && (
            <InlineNotification
              kind="success"
              title="Meeting secured!"
              subtitle="The client has accepted your meeting request."
              hideCloseButton
            />
          )}

          {meetingSecured && (
            <Button
              renderIcon={ArrowRight}
              onClick={() => navigate(`/dashboard/engagements/${engagementId}/preparation`)}
            >
              Continue to Meeting Preparation
            </Button>
          )}

          {!meetingSecured && (
            <form onSubmit={handleSubmit(onSubmit)}>
              <Tile>
                <Stack gap={4}>
                  <TextInput
                    id="subject"
                    labelText="Subject"
                    invalid={Boolean(errors.subject)}
                    invalidText={errors.subject?.message}
                    {...register('subject')}
                  />
                  <TextArea
                    id="body"
                    labelText="Message"
                    rows={8}
                    helperText="Use your research to personalise. Minimum 50 characters."
                    invalid={Boolean(errors.body)}
                    invalidText={errors.body?.message}
                    {...register('body')}
                  />
                  {sendOutreach.isError && (
                    <InlineNotification
                      kind="error"
                      title="Failed to send"
                      subtitle="Check engagement state and try again."
                      hideCloseButton
                    />
                  )}
                  <Button type="submit" renderIcon={Send} disabled={sendOutreach.isPending}>
                    {sendOutreach.isPending ? 'Sending…' : 'Send Outreach'}
                  </Button>
                </Stack>
              </Tile>
            </form>
          )}
        </Stack>
      </Column>

      <Column lg={6} md={3} sm={4}>
        <Stack gap={4}>
          <h3 style={{ color: '#161616' }}>Reply Thread</h3>
          {!attempts?.length && (
            <p style={{ color: '#525252' }}>No outreach sent yet.</p>
          )}
          {attempts?.map((a) => <AttemptThread key={a.id} attempt={a} />)}
        </Stack>
      </Column>
    </Grid>
  )
}
