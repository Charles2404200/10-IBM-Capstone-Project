import { useEffect, useState } from 'react'
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
  ProgressBar,
  InlineNotification,
  Tag,
} from '@carbon/react'
import { Add, TrashCan, ArrowRight } from '@carbon/icons-react'
import {
  useMeetingPreparation,
  useUpdateMeetingPreparation,
  useStartMeeting,
} from '@/api/hooks/useMeeting'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'

function EditableList({
  label,
  items,
  onChange,
  placeholder,
}: {
  label: string
  items: string[]
  onChange: (items: string[]) => void
  placeholder: string
}) {
  const update = (index: number, value: string) => {
    const next = [...items]
    next[index] = value
    onChange(next)
  }
  const remove = (index: number) => onChange((items ?? []).filter((_, i) => i !== index))
  const add = () => onChange([...items, ''])

  return (
    <Stack gap={3}>
      <h5 style={{ color: '#161616' }}>{label}</h5>
      {items.map((item, index) => (
        <div key={index} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <TextInput
            id={`${label}-${index}`}
            labelText=""
            hideLabel
            placeholder={placeholder}
            value={item}
            onChange={(e) => update(index, e.target.value)}
          />
          <Button
            kind="ghost"
            size="sm"
            iconDescription="Remove"
            hasIconOnly
            renderIcon={TrashCan}
            onClick={() => remove(index)}
          />
        </div>
      ))}
      <Button kind="tertiary" size="sm" renderIcon={Add} onClick={add}>
        Add {label.slice(0, -1)}
      </Button>
    </Stack>
  )
}

export default function MeetingPreparationPage() {
  const { engagementId } = useParams<{ engagementId: string }>()
  const navigate = useNavigate()
  const { data: preparation, isLoading, isError } = useMeetingPreparation(engagementId!)
  const updatePreparation = useUpdateMeetingPreparation(engagementId!)
  const startMeeting = useStartMeeting(engagementId!)

  const [objective, setObjective] = useState('')
  const [agenda, setAgenda] = useState<string[]>([])
  const [discoveryQuestions, setDiscoveryQuestions] = useState<string[]>([])

  useEffect(() => {
    if (preparation) {
      setObjective(preparation.objective ?? '')
      setAgenda(preparation.agenda.length ? preparation.agenda : [''])
      setDiscoveryQuestions(
        preparation.discoveryQuestions.length ? preparation.discoveryQuestions : ['']
      )
    }
  }, [preparation])

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  const handleSave = () => {
    updatePreparation.mutate({
      objective,
      agenda: (agenda ?? []).filter((a) => a.trim()),
      discoveryQuestions: (discoveryQuestions ?? []).filter((q) => q.trim()),
    })
  }

  const handleStartMeeting = () => {
    startMeeting.mutate(undefined, {
      onSuccess: (meeting) => navigate(`/dashboard/engagements/${engagementId}/meetings/${meeting.id}`),
    })
  }

  const readinessScore = preparation?.readinessScore ?? 0
  const ready = preparation?.ready ?? false

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={6}>
          <div>
            <Heading>Meeting Preparation</Heading>
            <p style={{ color: '#525252', marginTop: '0.5rem' }}>
              Define your objective, agenda and discovery questions before the live client meeting.
            </p>
          </div>

          <Tile>
            <Stack gap={3}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h5 style={{ color: '#161616' }}>Readiness Score</h5>
                <Tag type={ready ? 'green' : 'gray'}>{ready ? 'Ready' : 'Not ready'}</Tag>
              </div>
              <ProgressBar label="" hideLabel value={readinessScore} max={100} size="small" />
              <p style={{ color: '#525252', fontSize: '0.75rem' }}>{readinessScore}/100 — 70+ required to start</p>
            </Stack>
          </Tile>

          <Tile>
            <Stack gap={5}>
              <TextArea
                id="objective"
                labelText="Meeting objective"
                rows={3}
                value={objective}
                onChange={(e) => setObjective(e.target.value)}
              />
              <EditableList label="Agenda items" items={agenda} onChange={setAgenda} placeholder="e.g. Introductions" />
              <EditableList
                label="Discovery questions"
                items={discoveryQuestions}
                onChange={setDiscoveryQuestions}
                placeholder="e.g. What is your current budget?"
              />

              {updatePreparation.isError && (
                <InlineNotification kind="error" title="Failed to save preparation" hideCloseButton />
              )}

              <div style={{ display: 'flex', gap: '1rem' }}>
                <Button kind="secondary" disabled={updatePreparation.isPending} onClick={handleSave}>
                  {updatePreparation.isPending ? 'Saving…' : 'Save Preparation'}
                </Button>
                <Button
                  renderIcon={ArrowRight}
                  disabled={!ready || startMeeting.isPending}
                  onClick={handleStartMeeting}
                >
                  {startMeeting.isPending ? 'Starting…' : 'Start Meeting'}
                </Button>
              </div>
            </Stack>
          </Tile>
        </Stack>
      </Column>
    </Grid>
  )
}
