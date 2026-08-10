/**
 * The office floor page — the new home screen for an engagement.
 *
 * Everything reachable by walking is also reachable by tabbing through the
 * station list underneath the canvas. The world is the pleasurable path, never
 * the only one.
 */
import { useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, InlineNotification, Toggle } from '@carbon/react'
import { useMyEngagements } from '@/api/hooks/useEngagements'
import { usePersonaState } from '@/api/hooks/useMeeting'
import { useAuthStore } from '@/store/authStore'
import LoadingState from '@/components/shared/LoadingState'
import type { Engagement } from '@/api/types'
import type { SarahMood } from '../art/actors'
import { audio } from '../audio/engine'
import { useGameStore } from '../state/gameStore'
import { PHASE_BRIEF, stationStatus, type StationStatus } from '../state/progression'
import { STATIONS, type StationPlacement } from '../world/map'
import DayOne from './DayOne'
import HubWorld from './HubWorld'
import styles from '../styles/game.module.scss'

/** Chooses the engagement the world should represent: the one still in flight. */
export function selectActiveEngagement(engagements: Engagement[] | undefined): Engagement | null {
  if (!engagements || engagements.length === 0) return null
  const live = engagements.filter((e) => e.phase !== 'COMPLETED')
  const pool = live.length > 0 ? live : engagements
  return [...pool].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )[0]
}

/**
 * Maps the client's relationship state onto a facial expression.
 *
 * This is the cheapest possible win for perceived usefulness: the learner reads
 * the room before reading a number, exactly as they would with a real client.
 */
export function moodFor(trust: number | null, interest: number | null, patience: number | null): SarahMood {
  if (trust === null || interest === null || patience === null) return 'neutral'
  if (patience < 40) return 'impatient'
  if (trust < 50) return 'sceptical'
  if (trust >= 70 && interest >= 70) return 'engaged'
  return 'neutral'
}

const STATUS_LABEL: Record<StationStatus, string> = {
  done: 'Completed',
  current: 'You are here',
  locked: 'Locked',
  open: 'Always open',
}

export default function WorldPage() {
  const navigate = useNavigate()
  const displayName = useAuthStore((s) => s.displayName)
  const onboardingComplete = useGameStore((s) => s.onboardingComplete)
  const worldEnabled = useGameStore((s) => s.preferences.worldEnabled)
  const setPreferences = useGameStore((s) => s.setPreferences)
  const resetOnboarding = useGameStore((s) => s.resetOnboarding)

  const { data: engagements, isLoading } = useMyEngagements()
  const engagement = useMemo(() => selectActiveEngagement(engagements), [engagements])
  const { data: personaState } = usePersonaState(engagement?.meetingId ?? '')

  const mood = moodFor(
    personaState?.trust ?? null,
    personaState?.interest ?? null,
    personaState?.patience ?? null
  )

  const enter = useCallback(
    (station: StationPlacement | (typeof STATIONS)[number]) => {
      const status = stationStatus(station.phase, engagement)
      if (status === 'locked') {
        audio.play('deny')
        return
      }
      navigate(station.route(engagement?.id ?? null))
    },
    [engagement, navigate]
  )

  if (isLoading) return <LoadingState />

  if (!onboardingComplete) {
    return <DayOne defaultName={displayName ?? ''} onFinish={() => undefined} />
  }

  const brief = engagement ? PHASE_BRIEF[engagement.phase] : null

  return (
    <div style={{ maxWidth: '1280px', margin: '0 auto', padding: '1.5rem 2rem 3rem' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: '1rem',
          flexWrap: 'wrap',
        }}
      >
        <div>
          <h1 style={{ fontSize: '2rem', fontWeight: 300 }}>The floor</h1>
          <p style={{ color: '#525252', marginTop: '0.25rem' }}>
            {engagement
              ? `${engagement.leadCompanyName ?? engagement.scenarioTitle ?? 'Your engagement'} — ${
                  engagement.nextAction
                }`
              : 'No engagement running. Start one from the Command Centre in the lobby.'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <Toggle
            id="world-enabled"
            size="sm"
            labelText=""
            labelA="Map off"
            labelB="Map on"
            toggled={worldEnabled}
            onToggle={(checked) => setPreferences({ worldEnabled: checked })}
          />
          <Button kind="ghost" size="sm" onClick={resetOnboarding}>
            Replay intro
          </Button>
        </div>
      </div>

      {brief && (
        <div className={styles.brief} style={{ marginTop: '1.25rem' }}>
          <div className={styles.briefCell}>
            <p className={styles.briefLabel}>What this step is for</p>
            <p className={styles.briefText}>{brief.goal}</p>
          </div>
          <div className={styles.briefCell}>
            <p className={styles.briefLabel}>You are done when</p>
            <p className={styles.briefText}>{brief.done}</p>
          </div>
          <div className={styles.briefCell}>
            <p className={styles.briefLabel}>Then</p>
            <p className={styles.briefText}>{brief.next}</p>
          </div>
        </div>
      )}

      {worldEnabled ? (
        <HubWorld engagement={engagement} sarahMood={mood} onEnter={enter} />
      ) : (
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Map turned off"
          subtitle="Use the room list below. Turn the map back on at any time with the toggle above."
        />
      )}

      <h2 style={{ marginTop: '2rem', fontSize: '1.25rem', fontWeight: 400 }}>Rooms on this floor</h2>
      <p style={{ color: '#525252', fontSize: '0.875rem', marginTop: '0.25rem' }}>
        Walk to a room and press E, or use these buttons.
      </p>

      <ul className={styles.stationList}>
        {STATIONS.map((station) => {
          const status = stationStatus(station.phase, engagement)
          const locked = status === 'locked'
          return (
            <li key={station.glyph}>
              <button
                type="button"
                className={`${styles.stationCard} ${
                  status === 'current' ? styles.stationCardCurrent : ''
                } ${status === 'done' ? styles.stationCardDone : ''}`}
                onClick={() => enter(station)}
                disabled={locked || (!engagement && station.phase !== null)}
              >
                <span className={styles.stationStatus}>{STATUS_LABEL[status]}</span>
                <span className={styles.stationTitle}>{station.title}</span>
                <span className={styles.stationBlurb}>{station.blurb}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
