/**
 * A backend-free harness for reviewing the pixel layer.
 *
 * Mounted only under `import.meta.env.DEV`, so it never reaches a production
 * bundle. It exists so the team can look at the art, the walk cycle and the
 * lock states with `npm run dev` alone — no API, no login, no seeded database.
 */
import { useMemo, useState } from 'react'
import type { Engagement, EngagementPhase } from '@/api/types'
import type { SarahMood } from '../art/actors'
import { PHASE_LABEL, PHASE_ORDER } from '../state/progression'
import { placeStations, SPAWN } from '../world/map'
import HubWorld from './HubWorld'
import styles from '../styles/game.module.scss'

const MOODS: SarahMood[] = ['neutral', 'engaged', 'sceptical', 'impatient']

function mockEngagement(phase: EngagementPhase): Engagement {
  return {
    id: 'preview',
    userId: 'preview',
    scenarioId: 'preview',
    personaId: 'preview',
    state: 'OUTREACHING',
    selectedLeadId: 'preview',
    createdAt: new Date().toISOString(),
    completedAt: null,
    events: [],
    scenarioTitle: 'MediCare Digital Transformation',
    scenarioIndustry: 'Healthcare',
    leadCompanyName: 'MediCare Regional Hospital Network',
    phase,
    phaseLabel: PHASE_LABEL[phase],
    progressPercent: 0,
    nextAction: 'Preview',
    evidenceCount: 6,
    daysElapsed: 0,
    meetingId: null,
  }
}

export default function WorldPreview() {
  const [phase, setPhase] = useState<EngagementPhase>('OUTREACH')
  const [mood, setMood] = useState<SarahMood>('neutral')
  const [entered, setEntered] = useState<string | null>(null)
  const [startGlyph, setStartGlyph] = useState<string>('')

  const stations = useMemo(() => placeStations(), [])
  const spawnAt = useMemo(() => {
    const match = stations.find((s) => s.glyph === startGlyph)
    return match ? { tileX: match.tileX, tileY: match.tileY } : SPAWN
  }, [stations, startGlyph])

  return (
    <div style={{ padding: '1.5rem', maxWidth: '1280px', margin: '0 auto', fontFamily: 'IBM Plex Sans, sans-serif' }}>
      <h1 style={{ fontWeight: 300, fontSize: '1.75rem' }}>World preview</h1>
      <p style={{ color: '#525252', marginBottom: '1rem' }}>
        Development-only harness. Arrow keys or WASD to walk, E to enter a room.
      </p>

      <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
        <label style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', fontSize: '0.875rem' }}>
          Phase
          <select value={phase} onChange={(e) => setPhase(e.target.value as EngagementPhase)}>
            {PHASE_ORDER.map((p) => (
              <option key={p} value={p}>
                {PHASE_LABEL[p]}
              </option>
            ))}
          </select>
        </label>

        <label style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', fontSize: '0.875rem' }}>
          Client mood
          <select value={mood} onChange={(e) => setMood(e.target.value as SarahMood)}>
            {MOODS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>

        <label style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', fontSize: '0.875rem' }}>
          Stand at
          <select value={startGlyph} onChange={(e) => setStartGlyph(e.target.value)}>
            <option value="">Lobby (spawn)</option>
            {stations.map((s) => (
              <option key={s.glyph} value={s.glyph}>
                {s.title}
              </option>
            ))}
          </select>
        </label>

        {entered && (
          <span className={styles.stationStatus} style={{ alignSelf: 'center' }}>
            Entered: {entered}
          </span>
        )}
      </div>

      <HubWorld
        engagement={mockEngagement(phase)}
        sarahMood={mood}
        spawnAt={spawnAt}
        onEnter={(station) => setEntered(station.title)}
      />
    </div>
  )
}
