/**
 * The persistent heads-up display.
 *
 * Two pieces of state follow the learner across every screen:
 *
 *  1. Where they are in the ten-phase lifecycle.
 *  2. How the client currently feels about them.
 *
 * Both already exist server-side. Before this component, the phase stepper
 * appeared on some workspaces and not others, and trust/interest/patience were
 * visible only while inside the live meeting — so for most of a session the
 * learner had no way to see the two variables their decisions were moving.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useEngagement } from '@/api/hooks/useEngagements'
import { usePersonaState } from '@/api/hooks/useMeeting'
import type { EngagementPhase, PersonaState } from '@/api/types'
import { audio } from '../audio/engine'
import { useGameStore } from '../state/gameStore'
import { PHASE_COUNT, PHASE_LABEL, PHASE_ORDER, phaseIndex } from '../state/progression'
import styles from '../styles/game.module.scss'

/** Matches the threshold the live meeting already uses, so the HUD and the
 *  meeting page never disagree about what "healthy" means. */
export const RELATIONSHIP_THRESHOLD = 70

/** Pulls the engagement id out of the URL. AppShell is the parent route, so it
 *  cannot read the child's params directly. */
export function engagementIdFromPath(pathname: string): string | null {
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

export default function GameHUD() {
  const location = useLocation()
  const navigate = useNavigate()
  const worldEnabled = useGameStore((s) => s.preferences.worldEnabled)
  const [expanded, setExpanded] = useState(false)
  const muted = useGameStore((s) => s.preferences.audio.muted)
  const setAudio = useGameStore((s) => s.setAudio)

  const engagementId = engagementIdFromPath(location.pathname)
  const { data: engagement } = useEngagement(engagementId ?? '')
  const { data: personaState } = usePersonaState(engagement?.meetingId ?? '')

  // Remember the previous relationship reading so the HUD can show a delta —
  // "trust 62 (-8)" teaches cause and effect in a way a bare number cannot.
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

  // The music tightens as the client's patience drains.
  useEffect(() => {
    if (!personaState) return
    audio.setTension(personaState.patience / 100)
  }, [personaState])

  useEffect(() => {
    audio.updateSettings({ muted })
  }, [muted])

  if (!worldEnabled) return null

  const currentIndex = engagement ? phaseIndex(engagement.phase) : -1

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
    audio.play('openPanel')
    navigate(routes[phase])
  }

  const notMetYet = 'Not measured yet — the client has to meet you first.'

  const currentPhase = engagement?.phase ?? null
  const stepNumber = currentPhase ? phaseIndex(currentPhase) + 1 : 0

  return (
    <div className={styles.hud}>
      {/* Compact summary: the only thing shown on a phone until expanded.
          "← Office" used to live here and duplicated the "Office floor" nav
          item two centimetres away, so it is gone. */}
      <button
        type="button"
        className={styles.hudSummary}
        onClick={() => setExpanded((open) => !open)}
        aria-expanded={expanded}
        aria-controls="hud-detail"
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
              style={{
                backgroundColor: personaState ? meterColour(personaState[key]) : '#525252',
              }}
            />
          ))}
        </span>
        <span className={styles.hudChevron} aria-hidden="true">
          {expanded ? '▲' : '▼'}
        </span>
      </button>

      <div className={styles.hudDetail} id="hud-detail" data-open={expanded ? 'true' : 'false'}>
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
            hint={personaState ? 'Does the client believe you know what you are talking about?' : notMetYet}
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

        <button
          type="button"
          className={styles.iconButton}
          onClick={() => {
            audio.resume()
            setAudio({ muted: !muted })
          }}
          aria-pressed={muted}
          title={muted ? 'Unmute audio' : 'Mute audio'}
        >
          {muted ? '🔇 Muted' : '🔊 Sound'}
        </button>
        </div>
      </div>
    </div>
  )
}
