/**
 * "Day One" — the 90-second cold open that replaces having no onboarding.
 *
 * Four beats, in this order for a reason:
 *
 *  1. Identity   — you are a person with a job, not a user of a dashboard.
 *  2. The map    — the whole ten-phase lifecycle in plain English, on one screen,
 *                  before any jargon is used on you.
 *  3. One real action — a single research pull that produces a real evidence card,
 *                  so the learner has succeeded at something before being asked
 *                  to do anything hard.
 *  4. The brief  — a named client, and a door to walk through.
 *
 * Beat 3 matters most: self-efficacy is the strongest predictor of perceived
 * ease of use, and it is built by doing, not by reading a tour tooltip.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { Button, TextInput } from '@carbon/react'
import { audio } from '../audio/engine'
import { avatarPreview } from './HubWorld'
import { AVATAR_PRESETS, useGameStore, type AvatarChoice } from '../state/gameStore'
import { PHASE_LABEL, PHASE_ORDER } from '../state/progression'
import { STATIONS } from '../world/map'
import styles from '../styles/game.module.scss'

const BEATS = ['identity', 'map', 'practise', 'brief'] as const
type Beat = (typeof BEATS)[number]

/** Plain-language description of each phase, keyed off the station blurbs so the
 *  intro and the world can never drift apart. */
const BLURB_BY_PHASE = new Map(
  STATIONS.filter((s) => s.phase !== null).map((s) => [s.phase, s.blurb])
)

function AvatarCanvas({ avatar }: { avatar: AvatarChoice }) {
  const ref = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    const host = ref.current
    if (!host) return
    const canvas = avatarPreview(avatar)
    canvas.className = styles.avatarCanvas
    host.replaceChildren(canvas)
  }, [avatar])
  return <div ref={ref} aria-hidden="true" />
}

export interface DayOneProps {
  /** Name from the authenticated session, used as the default. */
  defaultName: string
  onFinish: () => void
}

export default function DayOne({ defaultName, onFinish }: DayOneProps) {
  const completeOnboarding = useGameStore((s) => s.completeOnboarding)
  const [beat, setBeat] = useState<Beat>('identity')
  const [name, setName] = useState(defaultName)
  const [presetId, setPresetId] = useState(AVATAR_PRESETS[0].id)
  const [pulled, setPulled] = useState<string | null>(null)

  const avatar = useMemo<AvatarChoice>(() => {
    const preset = AVATAR_PRESETS.find((p) => p.id === presetId) ?? AVATAR_PRESETS[0]
    return { displayName: name, hair: preset.hair, suit: preset.suit, skin: preset.skin }
  }, [name, presetId])

  const index = BEATS.indexOf(beat)

  const advance = () => {
    audio.resume()
    audio.play('confirm')
    const next = BEATS[index + 1]
    if (next) setBeat(next)
  }

  const finish = () => {
    audio.resume()
    audio.play('unlock')
    completeOnboarding(avatar)
    onFinish()
  }

  const skip = () => {
    completeOnboarding(avatar)
    onFinish()
  }

  return (
    <div className={styles.onboarding} role="dialog" aria-modal="true" aria-label="Day one at IBM Consulting">
      <div className={styles.onboardingPanel}>
        {beat === 'identity' && (
          <>
            <p className={styles.onboardingKicker}>Day one</p>
            <h1 className={styles.onboardingTitle}>Welcome to IBM Consulting.</h1>
            <p className={styles.onboardingBody}>
              You have joined as an analyst. Nobody expects you to know how an engagement works yet —
              that is what this floor is for. You will run a real one end to end, and you are allowed
              to get it wrong here.
            </p>
            <p className={styles.onboardingBody}>First, your staff badge.</p>

            <div style={{ maxWidth: '20rem' }}>
              <TextInput
                id="dayone-name"
                labelText="Name on the badge"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={40}
              />
            </div>

            <div className={styles.avatarRow}>
              {AVATAR_PRESETS.map((preset) => {
                const selected = preset.id === presetId
                return (
                  <button
                    key={preset.id}
                    type="button"
                    className={`${styles.avatarOption} ${selected ? styles.avatarOptionSelected : ''}`}
                    onClick={() => {
                      audio.resume()
                      audio.play('blip')
                      setPresetId(preset.id)
                    }}
                    aria-pressed={selected}
                  >
                    <AvatarCanvas
                      avatar={{ displayName: '', hair: preset.hair, suit: preset.suit, skin: preset.skin }}
                    />
                    {preset.label}
                  </button>
                )
              })}
            </div>
          </>
        )}

        {beat === 'map' && (
          <>
            <p className={styles.onboardingKicker}>What the job actually is</p>
            <h1 className={styles.onboardingTitle}>Ten steps, one client.</h1>
            <p className={styles.onboardingBody}>
              Every engagement runs the same course. You will walk between these rooms on the office
              floor — each one is a step, and each one unlocks the next.
            </p>
            <ol className={styles.lifecycleMap}>
              {PHASE_ORDER.map((phase, i) => (
                <li
                  key={phase}
                  className={styles.lifecycleNode}
                  style={{ animationDelay: `${i * 70}ms` }}
                >
                  <span className={styles.lifecycleNodeName}>
                    {i + 1}. {PHASE_LABEL[phase]}
                  </span>
                  <span className={styles.lifecycleNodeBlurb}>
                    {BLURB_BY_PHASE.get(phase) ?? 'Recorded in your portfolio.'}
                  </span>
                </li>
              ))}
            </ol>
          </>
        )}

        {beat === 'practise' && (
          <>
            <p className={styles.onboardingKicker}>Try one thing</p>
            <h1 className={styles.onboardingTitle}>This is what research feels like.</h1>
            <p className={styles.onboardingBody}>
              Most of consulting is finding one fact that changes the conversation. Pull a thread
              below — this is a demo client, nothing here counts.
            </p>

            <div className={styles.avatarRow}>
              {[
                {
                  id: 'news',
                  label: 'Search company news',
                  result:
                    'Regional health board minutes, March: the client has a regulatory audit scheduled within six months.',
                },
                {
                  id: 'people',
                  label: 'Look up the leadership team',
                  result:
                    'Their CIO has been in post fourteen months and inherited a migration that was written off in 2022.',
                },
                {
                  id: 'tech',
                  label: 'Check their technology',
                  result:
                    'Four hospital systems, none of them talking to each other, all feeding one ageing records platform.',
                },
              ].map((action) => (
                <button
                  key={action.id}
                  type="button"
                  className={`${styles.avatarOption} ${pulled === action.result ? styles.avatarOptionSelected : ''}`}
                  onClick={() => {
                    audio.resume()
                    audio.play('evidence')
                    setPulled(action.result)
                  }}
                  style={{ maxWidth: '13rem' }}
                >
                  {action.label}
                </button>
              ))}
            </div>

            {pulled && (
              <div className={styles.consequence} style={{ background: '#262626', borderColor: '#0f62fe' }}>
                <p className={styles.consequenceTitle} style={{ color: '#f4f4f4' }}>
                  Evidence added to your dossier
                </p>
                <p className={styles.onboardingBody} style={{ margin: 0 }}>
                  {pulled}
                </p>
                <p className={styles.onboardingBody} style={{ marginTop: '0.75rem', fontSize: '0.875rem' }}>
                  That is the whole loop: pull a thread, keep what is useful, and use it when you
                  finally speak to them. Everything else is a bigger version of this.
                </p>
              </div>
            )}
          </>
        )}

        {beat === 'brief' && (
          <>
            <p className={styles.onboardingKicker}>Your first assignment</p>
            <h1 className={styles.onboardingTitle}>There is a live opportunity.</h1>
            <p className={styles.onboardingBody}>
              A regional hospital network is under pressure and someone needs to work out whether we
              can genuinely help them. That someone is you.
            </p>
            <p className={styles.onboardingBody}>
              Walk to a room and press <strong>E</strong> to go in. Locked rooms tell you what has to
              happen first. Your desk is in the lobby if you want to see everything you have running.
            </p>
          </>
        )}

        <div className={styles.onboardingActions}>
          {beat !== 'brief' ? (
            <>
              <Button kind="primary" onClick={advance} disabled={beat === 'practise' && !pulled}>
                {beat === 'practise' && !pulled ? 'Pull a thread first' : 'Continue'}
              </Button>
              <Button kind="ghost" onClick={skip}>
                Skip the introduction
              </Button>
            </>
          ) : (
            <Button kind="primary" onClick={finish}>
              Start work
            </Button>
          )}

          <div className={styles.onboardingProgress} aria-hidden="true">
            {BEATS.map((b, i) => (
              <span
                key={b}
                className={`${styles.onboardingPip} ${i <= index ? styles.onboardingPipActive : ''}`}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
