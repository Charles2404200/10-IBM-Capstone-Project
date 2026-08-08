import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Button,
  Tile,
  TextInput,
  TextArea,
  NumberInput,
  Tag,
  InlineNotification,
} from '@carbon/react'
import { Add, TrashCan, Send } from '@carbon/icons-react'
import { useForm, useFieldArray, Controller } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useProposal, useSubmitProposal } from '@/api/hooks/useProposal'
import LoadingState from '@/components/shared/LoadingState'

const schema = z.object({
  problemStatement: z.string().min(20, 'Describe the problem in at least 20 characters'),
  components: z
    .array(z.object({ value: z.string().min(1, 'Component cannot be empty') }))
    .min(1, 'Add at least one solution component'),
  budget: z.coerce.number().min(0, 'Budget must be zero or greater'),
  timelineWeeks: z.coerce.number().int().positive('Timeline must be at least 1 week'),
})

type FormValues = z.infer<typeof schema>

export default function ProposalStudioPage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()
  const { data: proposal, isLoading } = useProposal(engagementId!)
  const submitProposal = useSubmitProposal(engagementId!)

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { problemStatement: '', components: [{ value: '' }], budget: 0, timelineWeeks: 4 },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'components' })

  const [submitted, setSubmitted] = useState(false)

  if (isLoading) return <LoadingState />

  const existingProposal = proposal

  const onSubmit = (data: FormValues) => {
    submitProposal.mutate(
      {
        problemStatement: data.problemStatement,
        components: data.components.map((c) => c.value),
        budget: String(data.budget),
        timelineWeeks: data.timelineWeeks,
      },
      { onSuccess: () => setSubmitted(true) }
    )
  }

  if (existingProposal || submitted) {
    const p = existingProposal ?? submitProposal.data
    const won = p?.decision === 'WON'
    return (
      <Grid fullWidth style={{ padding: '2rem' }}>
        <Column lg={10} md={6} sm={4}>
          <Stack gap={5}>
            <Heading>Proposal Outcome</Heading>
            <Tile>
              <Stack gap={4}>
                <Tag type={won ? 'green' : 'red'} size="lg">
                  {won ? 'Contract Won' : 'Contract Lost'}
                </Tag>
                <p style={{ color: '#525252' }}>Alignment score: {p?.alignmentScore}/100</p>
                <p style={{ color: '#525252' }}>{p?.decisionRationale}</p>
                <Button onClick={() => navigate(`/dashboard/engagements/${engagementId}/assessment`)}>
                  View Assessment
                </Button>
              </Stack>
            </Tile>
          </Stack>
        </Column>
      </Grid>
    )
  }

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={10} md={6} sm={4}>
        <form onSubmit={handleSubmit(onSubmit)}>
          <Stack gap={6}>
            <div>
              <Heading>Proposal Studio</Heading>
              <p style={{ color: '#525252', marginTop: '0.5rem' }}>
                Craft a proposal grounded in what you discovered during research and the meeting.
              </p>
            </div>

            <Tile>
              <Stack gap={5}>
                <TextArea
                  id="problemStatement"
                  labelText="Problem statement"
                  rows={3}
                  invalid={Boolean(errors.problemStatement)}
                  invalidText={errors.problemStatement?.message}
                  {...register('problemStatement')}
                />

                <Stack gap={3}>
                  <h5 style={{ color: '#161616' }}>Solution components</h5>
                  {fields.map((field, index) => (
                    <div key={field.id} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                      <TextInput
                        id={`component-${index}`}
                        labelText=""
                        hideLabel
                        placeholder="e.g. Cloud migration roadmap"
                        {...register(`components.${index}.value` as const)}
                      />
                      <Button
                        kind="ghost"
                        size="sm"
                        hasIconOnly
                        iconDescription="Remove"
                        renderIcon={TrashCan}
                        onClick={() => remove(index)}
                      />
                    </div>
                  ))}
                  {errors.components && (
                    <p style={{ color: '#fa4d56', fontSize: '0.75rem' }}>{errors.components.message}</p>
                  )}
                  <Button kind="tertiary" size="sm" renderIcon={Add} onClick={() => append({ value: '' })}>
                    Add Component
                  </Button>
                </Stack>

                <div style={{ display: 'flex', gap: '1rem' }}>
                  <Controller
                    control={control}
                    name="budget"
                    render={({ field }) => (
                      <NumberInput
                        id="budget"
                        label="Budget (USD)"
                        min={0}
                        value={field.value}
                        onChange={(_, { value }) => field.onChange(value)}
                        invalid={Boolean(errors.budget)}
                        invalidText={errors.budget?.message}
                      />
                    )}
                  />
                  <Controller
                    control={control}
                    name="timelineWeeks"
                    render={({ field }) => (
                      <NumberInput
                        id="timelineWeeks"
                        label="Timeline (weeks)"
                        min={1}
                        value={field.value}
                        onChange={(_, { value }) => field.onChange(value)}
                        invalid={Boolean(errors.timelineWeeks)}
                        invalidText={errors.timelineWeeks?.message}
                      />
                    )}
                  />
                </div>

                {submitProposal.isError && (
                  <InlineNotification kind="error" title="Failed to submit proposal" hideCloseButton />
                )}

                <Button type="submit" renderIcon={Send} disabled={submitProposal.isPending}>
                  {submitProposal.isPending ? 'Submitting…' : 'Submit Proposal'}
                </Button>
              </Stack>
            </Tile>
          </Stack>
        </form>
      </Column>
    </Grid>
  )
}
