import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
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
import { Add, TrashCan, ArrowRight, CheckmarkFilled, ChevronLeft, ChevronRight } from '@carbon/icons-react'
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

const ITEMS_PER_PAGE = 3
const READY_THRESHOLD = 70

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
  const [page, setPage] = useState(0)
  const completedCount = items.filter((item) => item.value.trim()).length
  const totalPages = Math.max(1, Math.ceil(items.length / ITEMS_PER_PAGE))
  const visibleItems = items.slice(page * ITEMS_PER_PAGE, (page + 1) * ITEMS_PER_PAGE)

  useEffect(() => {
    setPage((currentPage) => Math.min(currentPage, totalPages - 1))
  }, [totalPages])

  const update = (index: number, value: string) => {
    onChange(items.map((item, itemIndex) => itemIndex === index ? { ...item, value } : item))
  }
  const remove = (index: number) => {
    const next = items.filter((_, itemIndex) => itemIndex !== index)
    onChange(next.length > 0 ? next : [createDraftItem()])
  }
  const add = () => {
    const next = [...items, createDraftItem()]
    onChange(next)
    setPage(Math.floor((next.length - 1) / ITEMS_PER_PAGE))
  }

  return (
    <section className={styles.listWorkspace} aria-label={label}>
      <div className={styles.listHeading}>
        <div>
          <p className={styles.listEyebrow}>Meeting plan</p>
          <h2>{label}</h2>
        </div>
        <Tag type={completedCount > 0 ? 'blue' : 'gray'} size="sm">{completedCount} drafted</Tag>
      </div>
      <div className={styles.itemsViewport}>
        {visibleItems.map((item, visibleIndex) => {
          const index = page * ITEMS_PER_PAGE + visibleIndex
          return (
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
          )
        })}
      </div>
      <div className={styles.listFooter}>
        <Button kind="tertiary" size="sm" renderIcon={Add} onClick={add}>
          Add {itemLabel.toLowerCase()}
        </Button>
        {totalPages > 1 && (
          <div className={styles.paginationControls} aria-label={`${label} pages`}>
            <span>{page + 1} of {totalPages}</span>
            <Button
              kind="ghost"
              size="sm"
              hasIconOnly
              renderIcon={ChevronLeft}
              iconDescription="Previous items"
              disabled={page === 0}
              onClick={() => setPage((currentPage) => currentPage - 1)}
            />
            <Button
              kind="ghost"
              size="sm"
              hasIconOnly
              renderIcon={ChevronRight}
              iconDescription="Next items"
              disabled={page === totalPages - 1}
              onClick={() => setPage((currentPage) => currentPage + 1)}
            />
          </div>
        )}
      </div>
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
  const [launchingMeeting, setLaunchingMeeting] = useState(false)
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

  const savePreparation = (onSuccess?: () => void) => {
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
        onSuccess?.()
      },
      onError: () => setLaunchingMeeting(false),
    })
  }

  const handleSave = () => savePreparation()

  const handleStartMeeting = () => {
    setLaunchingMeeting(true)
    savePreparation(() => {
      startMeeting.mutate(undefined, {
        onSuccess: (meeting) => navigate(`/dashboard/engagements/${engagementId}/meetings/${meeting.id}`),
        onError: () => setLaunchingMeeting(false),
      })
    })
  }

  const agendaCount = agenda.filter((item) => item.value.trim()).length
  const questionCount = discoveryQuestions.filter((item) => item.value.trim()).length
  const objectiveReady = objective.trim().length > 0
  const readinessScore = Math.min(100,
    (objectiveReady ? 20 : 0)
    + Math.min(40, agendaCount * 10)
    + Math.min(40, questionCount * 8),
  )
  const ready = readinessScore >= READY_THRESHOLD
  const isSaving = updatePreparation.isPending || launchingMeeting

  return (
    <div className={styles.page}>
      <div className={styles.canvas}>
        <header className={styles.pageHeader}>
          <div>
            <p className={styles.eyebrow}>Engagement workflow / step 4</p>
            <Heading>{PHASE_LABEL.MEETING_PREPARATION}</Heading>
            <p className={styles.pageDescription}>Turn your research into a focused client conversation with a clear purpose, flow and questions.</p>
          </div>
          <div className={styles.headerActions}>
            <Button kind="tertiary" disabled={isSaving} onClick={handleSave}>
              {updatePreparation.isPending && !launchingMeeting ? 'Saving...' : 'Save plan'}
            </Button>
            <Button renderIcon={ArrowRight} disabled={!ready || isSaving || startMeeting.isPending} onClick={handleStartMeeting}>
              {launchingMeeting ? 'Opening meeting...' : startMeeting.isPending ? 'Starting...' : 'Start meeting'}
            </Button>
          </div>
        </header>

        <section className={styles.readinessStrip} aria-label="Meeting readiness">
          <div className={styles.readinessScore}>
            <div>
              <p className={styles.eyebrow}>Readiness preview</p>
              <strong>{readinessScore}<span>/100</span></strong>
            </div>
            <Tag type={ready ? 'green' : 'gray'}>{ready ? 'Ready to meet' : `${READY_THRESHOLD - readinessScore} points to go`}</Tag>
          </div>
          <ProgressBar label="Meeting readiness" hideLabel value={readinessScore} max={100} size="small" />
          <div className={styles.readinessChecks}>
            <ReadinessCheck complete={objectiveReady} label="Meeting objective" detail="20 points" />
            <ReadinessCheck complete={agendaCount >= 3} label="Agenda flow" detail={`${agendaCount}/4 items`} />
            <ReadinessCheck complete={questionCount >= 3} label="Discovery questions" detail={`${questionCount}/5 questions`} />
          </div>
        </section>

        <main className={styles.workspaceGrid}>
          <section className={styles.primaryWorkspace} aria-label="Meeting plan workspace">
            <Tile className={styles.objectivePanel}>
              <div className={styles.panelHeading}>
                <div>
                  <p className={styles.eyebrow}>Conversation outcome</p>
                  <h2>Meeting objective</h2>
                </div>
                <span>{objective.trim().length}/300</span>
              </div>
              <TextArea
                id="objective"
                labelText="Meeting objective"
                hideLabel
                rows={3}
                maxLength={300}
                placeholder="e.g. Validate the operational problem, quantify its impact and agree a low-risk next step."
                value={objective}
                onChange={(event) => setObjective(event.target.value)}
              />
            </Tile>

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
          </section>

          <aside className={styles.coachingRail} aria-label="Preparation guidance">
            <Tile className={styles.coachingPanel}>
              <p className={styles.eyebrow}>What good looks like</p>
              <h2>A purposeful conversation</h2>
              <p>Start with the outcome the client cares about, then use questions to learn what you do not yet know.</p>
              <div className={styles.coachingTip}>
                <strong>Next best action</strong>
                <span>{objectiveReady ? agendaCount < 3 ? 'Add an agenda flow that gives the client a clear meeting structure.' : questionCount < 3 ? 'Add open discovery questions that uncover impact, constraints and decision criteria.' : 'Your plan is ready. Start the meeting when you are prepared.' : 'Write the business outcome you need to validate in this conversation.'}</span>
              </div>
            </Tile>

            <Tile className={styles.scoreGuide}>
              <p className={styles.eyebrow}>Score guide</p>
              <div><span>Objective</span><strong>20 pts</strong></div>
              <div><span>Agenda</span><strong>Up to 40 pts</strong></div>
              <div><span>Questions</span><strong>Up to 40 pts</strong></div>
              <p>Reach 70 to open the live meeting. Your plan is saved automatically in this browser while you work.</p>
            </Tile>

            {updatePreparation.isError && (
              <InlineNotification className={styles.errorNotification} kind="error" lowContrast title="Failed to save preparation" subtitle="Your local draft is still available. Try saving again." hideCloseButton />
            )}
          </aside>
        </main>
      </div>
    </div>
  )
}

function ReadinessCheck({ complete, label, detail }: { complete: boolean; label: string; detail: string }) {
  return (
    <div className={styles.readinessCheck}>
      <CheckmarkFilled className={complete ? styles.checkComplete : styles.checkPending} />
      <div>
        <strong>{label}</strong>
        <span>{detail}</span>
      </div>
    </div>
  )
}
