import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Grid,
  Column,
  Heading,
  Button,
  Tile,
  TextInput,
  TextArea,
  ProgressBar,
  InlineNotification,
  Tag,
  Tabs,
  TabList,
  Tab,
  TabPanels,
  TabPanel,
} from '@carbon/react'
import { Add, TrashCan, ArrowRight } from '@carbon/icons-react'
import {
  useMeetingPreparation,
  useUpdateMeetingPreparation,
  useStartMeeting,
} from '@/api/hooks/useMeeting'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import styles from './MeetingPreparationPage.module.scss'
import { PHASE_LABEL } from '@/lifecycle/phases'

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
  itemLabel,
  items,
  onChange,
  placeholder,
}: {
  label: string
  itemLabel: string
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
    <section className={styles.listWorkspace} aria-label={label}>
      <div className={styles.listHeading}>
        <h2>{label}</h2>
        <Tag type="gray" size="sm">{items.filter((item) => item.value.trim()).length}</Tag>
      </div>
      <div className={styles.itemsViewport}>
        {items.map((item, index) => (
          <div key={item.id} className={styles.listRow}>
            <span className={styles.rowNumber}>{index + 1}</span>
            <TextInput
              id={`${label}-${item.id}`}
              labelText={`${itemLabel} ${index + 1}`}
              hideLabel
              placeholder={placeholder}
              value={item.value}
              onChange={(event) => update(index, event.target.value)}
            />
            <Button
              kind="ghost"
              size="sm"
              iconDescription={`Remove ${itemLabel.toLowerCase()} ${index + 1}`}
              hasIconOnly
              renderIcon={TrashCan}
              onClick={() => remove(index)}
            />
          </div>
        ))}
      </div>
      <Button className={styles.addItemButton} kind="tertiary" size="sm" renderIcon={Add} onClick={add}>
        Add {itemLabel.toLowerCase()}
      </Button>
    </section>
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
  const agendaCount = agenda.filter((item) => item.value.trim()).length
  const questionCount = discoveryQuestions.filter((item) => item.value.trim()).length

  return (
    <div className={styles.page}>
      <Grid fullWidth className={styles.headerGrid}>
        <Column lg={16} md={8} sm={4}>
          <div className={styles.pageHeader}>
            <div>
              <Heading>{PHASE_LABEL.MEETING_PREPARATION}</Heading>
              <p>Define the meeting plan before the live client conversation.</p>
            </div>
            <div className={styles.headerActions}>
              <Button kind="secondary" disabled={updatePreparation.isPending} onClick={handleSave}>
                {updatePreparation.isPending ? 'Saving...' : 'Save Preparation'}
              </Button>
              <Button renderIcon={ArrowRight} disabled={!ready || startMeeting.isPending} onClick={handleStartMeeting}>
                {startMeeting.isPending ? 'Starting...' : 'Start Meeting'}
              </Button>
            </div>
          </div>
        </Column>
      </Grid>

      <Grid fullWidth className={styles.workspaceGrid}>
        <Column lg={5} md={8} sm={4} className={styles.planColumn}>
          <section className={styles.readinessPanel} aria-label="Meeting readiness">
            <div className={styles.readinessHeading}>
              <div>
                <p className={styles.eyebrow}>Meeting readiness</p>
                <strong>{readinessScore}<span>/100</span></strong>
              </div>
              <Tag type={ready ? 'green' : 'gray'}>{ready ? 'Ready' : 'Not ready'}</Tag>
            </div>
            <ProgressBar label="" hideLabel value={readinessScore} max={100} size="small" />
            <p>70 required to start</p>
          </section>

          <Tile className={styles.objectivePanel}>
            <TextArea
              id="objective"
              labelText="Meeting objective"
              rows={7}
              value={objective}
              onChange={(event) => setObjective(event.target.value)}
            />
          </Tile>

          {updatePreparation.isError && (
            <InlineNotification className={styles.errorNotification} kind="error" lowContrast title="Failed to save preparation" hideCloseButton />
          )}
        </Column>

        <Column lg={11} md={8} sm={4} className={styles.editorColumn}>
          <Tile className={styles.collectionPanel}>
            <Tabs>
              <TabList aria-label="Meeting plan sections" className={styles.sectionTabs}>
                <Tab>Agenda <span className={styles.tabCount}>{agendaCount}</span></Tab>
                <Tab>Discovery questions <span className={styles.tabCount}>{questionCount}</span></Tab>
              </TabList>
              <TabPanels>
                <TabPanel className={styles.tabPanel}>
                  <EditableList
                    label="Agenda"
                    itemLabel="Agenda item"
                    items={agenda}
                    onChange={setAgenda}
                    placeholder="e.g. Confirm meeting objectives"
                  />
                </TabPanel>
                <TabPanel className={styles.tabPanel}>
                  <EditableList
                    label="Discovery questions"
                    itemLabel="Question"
                    items={discoveryQuestions}
                    onChange={setDiscoveryQuestions}
                    placeholder="e.g. Which operational issue has the highest impact?"
                  />
                </TabPanel>
              </TabPanels>
            </Tabs>
          </Tile>
        </Column>
      </Grid>
    </div>
  )
}
