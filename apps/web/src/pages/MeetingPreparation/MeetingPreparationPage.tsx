import { useEffect, useRef, useState } from 'react'
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

interface DraftListItem {
  id: string
  value: string
}

interface PreparationDraft {
  objective: string
  agenda: string[]
  discoveryQuestions: string[]
}

let generatedItemId = 0

function createDraftItem(value = ''): DraftListItem {
  generatedItemId += 1
  return {
    id: globalThis.crypto?.randomUUID?.() ?? `preparation-item-${Date.now()}-${generatedItemId}`,
    value,
  }
}

function toDraftItems(values: string[]): DraftListItem[] {
  const items = values.map(createDraftItem)
  return items.length > 0 ? items : [createDraftItem()]
}

function draftStorageKey(engagementId: string) {
  return `consulting-sim:meeting-preparation:${engagementId}`
}

function readDraft(engagementId: string): PreparationDraft | null {
  try {
    const raw = window.localStorage.getItem(draftStorageKey(engagementId))
    if (!raw) return null
    const draft = JSON.parse(raw) as PreparationDraft
    if (!Array.isArray(draft.agenda) || !Array.isArray(draft.discoveryQuestions) || typeof draft.objective !== 'string') {
      return null
    }
    return draft
  } catch {
    return null
  }
}

function EditableList({
  label,
  items,
  onChange,
  placeholder,
}: {
  label: string
  items: DraftListItem[]
  onChange: (items: DraftListItem[]) => void
  placeholder: string
}) {
  const update = (index: number, value: string) => {
    onChange(items.map((item, itemIndex) => itemIndex === index ? { ...item, value } : item))
  }
  const remove = (index: number) => {
    const next = items.filter((_, itemIndex) => itemIndex !== index)
    onChange(next.length > 0 ? next : [createDraftItem()])
  }
  const add = () => onChange([...items, createDraftItem()])

  return (
    <Stack gap={3}>
      <h5 style={{ color: '#161616' }}>{label}</h5>
      {items.map((item, index) => (
        <div key={item.id} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <TextInput
            id={`${label}-${index}`}
            labelText=""
            hideLabel
            placeholder={placeholder}
            value={item.value}
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
  const [agenda, setAgenda] = useState<DraftListItem[]>([])
  const [discoveryQuestions, setDiscoveryQuestions] = useState<DraftListItem[]>([])
  const hydratedEngagementRef = useRef<string | null>(null)
  const hasHydratedRef = useRef(false)

  useEffect(() => {
    if (!preparation || !engagementId || hydratedEngagementRef.current === engagementId) return

    const draft = readDraft(engagementId)
    const source = draft ?? {
      objective: preparation.objective ?? '',
      agenda: preparation.agenda,
      discoveryQuestions: preparation.discoveryQuestions,
    }
    setObjective(source.objective)
    setAgenda(toDraftItems(source.agenda))
    setDiscoveryQuestions(toDraftItems(source.discoveryQuestions))
    hydratedEngagementRef.current = engagementId
    hasHydratedRef.current = true
  }, [engagementId, preparation])

  useEffect(() => {
    if (!engagementId || !hasHydratedRef.current || hydratedEngagementRef.current !== engagementId) return

    const timer = window.setTimeout(() => {
      const draft: PreparationDraft = {
        objective,
        agenda: agenda.map((item) => item.value),
        discoveryQuestions: discoveryQuestions.map((item) => item.value),
      }
      window.localStorage.setItem(draftStorageKey(engagementId), JSON.stringify(draft))
    }, 300)
    return () => window.clearTimeout(timer)
  }, [agenda, discoveryQuestions, engagementId, objective])

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  const handleSave = () => {
    updatePreparation.mutate({
      objective,
      agenda: agenda.map((item) => item.value).filter((value) => value.trim()),
      discoveryQuestions: discoveryQuestions.map((item) => item.value).filter((value) => value.trim()),
    }, {
      onSuccess: (saved) => {
        window.localStorage.setItem(draftStorageKey(engagementId!), JSON.stringify({
          objective: saved.objective ?? '',
          agenda: saved.agenda,
          discoveryQuestions: saved.discoveryQuestions,
        } satisfies PreparationDraft))
      },
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
