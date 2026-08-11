/**
 * The persistent engagement bar.
 *
 * Two things follow the learner across every workspace: where they are in the
 * ten-step lifecycle, and how the client currently feels about them.
 *
 * Both already exist server-side and neither was reachable where it mattered.
 * The phase stepper rendered on Client Intelligence and nowhere else, and
 * `PersonaState` — trust, interest, patience — was only drawn inside the live
 * meeting. That is one phase out of ten; for the other nine the learner could
 * not see the state their decisions were moving, which is the difference
 * between a simulation that teaches consequence and one that merely has it.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useEngagement, useMyEngagements } from '@/api/hooks/useEngagements'
import { usePersonaState } from '@/api/hooks/useMeeting'
import type { EngagementPhase, PersonaState } from '@/api/types'
import { selectActiveEngagement } from '../activeEngagement'
import { PHASE_COUNT, PHASE_LABEL, PHASE_ORDER, phaseIndex } from '../phases'
import styles from '../lifecycle.module.scss'

/** Matches the threshold the live meeting already uses, so the bar and the
 *  meeting page never disagree about what "healthy" means. */
const RELATIONSHIP_THRESHOLD = 70

/** Pulls the engagement id out of the URL. AppShell is the parent route, so it
 *  cannot read a child route's params directly. */
function engagementIdFromPath(pathname: string): string | null {
  return /\/dashboard\/engagements\/([^/]+)/.exec(pathname)?.[1] ?? null
}

function meterColour(value: number): string {
  if (value >= RELATIONSHIP_THRESHOLD) return '#24a148'
  if (value >= 50) return '#f1c21b'
  return '#da1e28'
}

interface MeterProps {
  label: string
  value: number | null
  delta: number | null
  hint: string
}

function Meter({ label, value, delta, hint }: MeterProps) {
  return (
    <div className={styles.meter} title={hint}>
      <div className={styles.meterHead}>
        <span>{label}</span>
        {value === null ? (
          <span className={styles.meterUnknown}>—</span>
        ) : (
          <span>
            {value}
            {delta !== null && delta !== 0 && (
              <span
                className={`${styles.meterDelta} ${delta > 0 ? styles.meterDeltaUp : styles.meterDeltaDown}`}
              >
                {' '}
                {delta > 0 ? '+' : ''}
                {delta}
              </span>
            )}
          </span>
        )}
      </div>
      <div className={styles.meterTrack}>
        <div
          className={styles.meterFill}
          style={{
            width: `${value ?? 0}%`,
            backgroundColor: value === null ? '#525252' : meterColour(value),
          }}
        />
      </div>
    </div>
  )
}

interface StepperProps {
  currentPhase: EngagementPhase | null
  onJump: (phase: EngagementPhase) => void
  canJump: (phase: EngagementPhase) => boolean
}

function Stepper({ currentPhase, onJump, canJump }: StepperProps) {
  const currentIndex = currentPhase ? phaseIndex(currentPhase) : -1

  return (
    <ol className={styles.stepper} aria-label="Engagement progress">
      {PHASE_ORDER.map((phase, index) => {
        const done = currentIndex > index
        const current = currentIndex === index
        const actionable = canJump(phase)
        return (
          <li key={phase} className={styles.step}>
            {index > 0 && (
              <span
                className={`${styles.stepConnector} ${done || current ? styles.stepConnectorDone : ''}`}
                aria-hidden="true"
              />
            )}
            <button
              type="button"
              className={`${styles.stepButton} ${actionable ? styles.stepButtonActionable : ''}`}
              onClick={() => actionable && onJump(phase)}
              disabled={!actionable}
              aria-current={current ? 'step' : undefined}
              title={
                done
                  ? `${PHASE_LABEL[phase]} — completed`
                  : current
                    ? `${PHASE_LABEL[phase]} — you are here`
                    : `${PHASE_LABEL[phase]} — not unlocked yet`
              }
            >
              <span
                className={`${styles.stepDot} ${done ? styles.stepDotDone : ''} ${
                  current ? styles.stepDotCurrent : ''
                }`}
                aria-hidden="true"
              />
              <span
                className={`${styles.stepLabel} ${
                  current ? styles.stepCurrentLabel : done ? styles.stepDoneLabel : ''
                }`}
              >
                {PHASE_LABEL[phase]}
              </span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}

export default function EngagementHUD() {
  const location = useLocation()
  const navigate = useNavigate()
  const [expanded, setExpanded] = useState(false)

  // Workspace routes carry the id; /dashboard does not, and there the bar used
  // to render an empty stepper — on the screen a learner is most likely to be
  // checking their bearings.
  const engagementId = engagementIdFromPath(location.pathname)
  const { data: routeEngagement } = useEngagement(engagementId ?? '')
  const { data: allEngagements } = useMyEngagements()
  const engagement = engagementId
    ? routeEngagement
    : (selectActiveEngagement(allEngagements) ?? undefined)

  const { data: personaState } = usePersonaState(engagement?.meetingId ?? '')

  // Remember the previous reading so the bar can show a delta — "trust 62 (−8)"
  // teaches cause and effect in a way a bare number cannot.
  const previousRef = useRef<PersonaState | null>(null)
  const deltas = useMemo(() => {
    const previous = previousRef.current
    if (!personaState || !previous) return { trust: null, interest: null, patience: null }
    return {
      trust: personaState.trust - previous.trust,
      interest: personaState.interest - previous.interest,
      patience: personaState.patience - previous.patience,
    }
  }, [personaState])

  useEffect(() => {
    if (personaState) previousRef.current = personaState
  }, [personaState])

  const currentPhase = engagement?.phase ?? null
  const currentIndex = currentPhase ? phaseIndex(currentPhase) : -1
  const stepNumber = currentIndex + 1

  const canJump = (phase: EngagementPhase) =>
    Boolean(engagement) && phaseIndex(phase) <= currentIndex

  const jump = (phase: EngagementPhase) => {
    if (!engagement) return
    const base = `/dashboard/engagements/${engagement.id}`
    const routes: Record<EngagementPhase, string> = {
      LEAD: `${base}/leads`,
      CLIENT_INTELLIGENCE: `${base}/intelligence`,
      OUTREACH: `${base}/outreach`,
      MEETING_PREPARATION: `${base}/preparation`,
      LIVE_MEETING: engagement.meetingId
        ? `${base}/meetings/${engagement.meetingId}`
        : `${base}/preparation`,
      MEETING_REVIEW: `${base}/assessment`,
      PROPOSAL: `${base}/proposal`,
      OUTCOME: `${base}/assessment`,
      REVIEW: `${base}/assessment`,
      COMPLETED: '/dashboard/portfolio',
    }
    navigate(routes[phase])
  }

  const notMetYet = 'Not measured yet — the client has to meet you first.'

  return (
    <div className={styles.hud}>
      {/* Compact summary: the only thing shown on a phone until expanded. */}
      <button
        type="button"
        className={styles.hudSummary}
        onClick={() => setExpanded((open) => !open)}
        aria-expanded={expanded}
        aria-controls="engagement-hud-detail"
      >
        <span className={styles.hudStep}>
          {currentPhase ? `${stepNumber}/${PHASE_COUNT}` : '—'}
        </span>
        <span className={styles.hudStepName}>
          {currentPhase ? PHASE_LABEL[currentPhase] : 'No engagement running'}
        </span>
        <span className={styles.hudDots} aria-hidden="true">
          {(['trust', 'interest', 'patience'] as const).map((key) => (
            <span
              key={key}
              className={styles.hudDot}
              style={{ backgroundColor: personaState ? meterColour(personaState[key]) : '#525252' }}
            />
          ))}
        </span>
        <span className={styles.hudChevron} aria-hidden="true">
          {expanded ? '▲' : '▼'}
        </span>
      </button>

      <div
        className={styles.hudDetail}
        id="engagement-hud-detail"
        data-open={expanded ? 'true' : 'false'}
      >
        <div className={`${styles.hudGroup} ${styles.hudGrow}`}>
          <Stepper currentPhase={currentPhase} onJump={jump} canJump={canJump} />
        </div>

        <div className={styles.hudGroup}>
          {engagement?.nextAction && (
            <span className={styles.hudNext} title="Your next action">
              {engagement.nextAction}
            </span>
          )}
        </div>

        <div className={styles.hudGroup}>
          <span className={styles.hudLabel}>Client</span>
          <div className={styles.meters}>
            <Meter
              label="Trust"
              value={personaState?.trust ?? null}
              delta={deltas.trust}
              hint={
                personaState
                  ? 'Does the client believe you know what you are talking about?'
                  : notMetYet
              }
            />
            <Meter
              label="Interest"
              value={personaState?.interest ?? null}
              delta={deltas.interest}
              hint={personaState ? 'Do they think this is worth their time?' : notMetYet}
            />
            <Meter
              label="Patience"
              value={personaState?.patience ?? null}
              delta={deltas.patience}
              hint={personaState ? 'How much longer they will tolerate the conversation.' : notMetYet}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
