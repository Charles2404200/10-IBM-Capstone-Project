/**
 * The walkable hub. Owns the canvas, the input handling and the frame loop, and
 * reports station proximity upwards so the surrounding page can render the
 * (accessible, DOM-based) interaction prompt.
 *
 * Accessibility note: the canvas is never the only route to a station. The
 * containing page renders a real list of buttons for every station, and this
 * component is skippable entirely via the "world" preference. The pixel layer is
 * an enhancement, not a gate.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Engagement } from '@/api/types'
import styles from '../styles/game.module.scss'
import { PLAYER_DOWN, PLAYER_RIGHT, PLAYER_UP, SARAH_MOODS, playerPose, type SarahMood } from '../art/actors'
import { createSpriteCache, rasterise, recolour, type Sprite } from '../art/pixels'
import { TILE } from '../art/tiles'
import { audio } from '../audio/engine'
import { useGameStore, type AvatarChoice } from '../state/gameStore'
import { stationStatus } from '../state/progression'
import {
  axisFromKeys,
  cameraFor,
  initialPlayer,
  INTERACT_KEYS,
  isMoveKey,
  nearestStation,
  stationPlacements,
  step,
  type PlayerState,
} from '../world/engine'
import { SARAH_POSITION } from '../world/map'
import {
  buildMapCanvas,
  bubbleCanvas,
  drawFrame,
  type ActorDraw,
  type BubbleKind,
} from '../world/renderer'
import type { StationPlacement } from '../world/map'

export interface HubWorldProps {
  engagement: Engagement | null
  /** Mood for the persona standing in the meeting room. */
  sarahMood: SarahMood
  /** Fired when the player presses the interact key on an available station. */
  onEnter: (station: StationPlacement) => void
  /** Fired whenever the highlighted station changes, including to null. */
  onFocusChange?: (station: StationPlacement | null, status: ReturnType<typeof stationStatus>) => void
  /** Optional override for where the avatar stands. Used by the dev preview
   *  harness to inspect a specific room without walking there. */
  spawnAt?: { tileX: number; tileY: number }
}

function avatarSprite(base: Sprite, avatar: AvatarChoice): Sprite {
  return recolour(base, { h: avatar.hair, b: avatar.suit, s: avatar.skin })
}

export default function HubWorld({
  engagement,
  sarahMood,
  onEnter,
  onFocusChange,
  spawnAt,
}: HubWorldProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const wrapperRef = useRef<HTMLDivElement | null>(null)
  const playerRef = useRef<PlayerState>(initialPlayer())
  const heldRef = useRef<Set<string>>(new Set())
  const rafRef = useRef<number | null>(null)
  const lastTimeRef = useRef<number>(0)
  const stepSoundRef = useRef<number>(0)
  const focusedRef = useRef<string | null>(null)

  const avatar = useGameStore((s) => s.avatar)
  const reducedMotion = useGameStore((s) => s.preferences.reducedMotion)
  const markVisited = useGameStore((s) => s.markVisited)

  const [prompt, setPrompt] = useState<{ station: StationPlacement; locked: boolean } | null>(null)

  const mapCanvas = useMemo(() => buildMapCanvas(), [])
  const spriteCache = useMemo(() => createSpriteCache(), [])
  const bubbles = useMemo(
    () => ({
      available: bubbleCanvas('available'),
      locked: bubbleCanvas('locked'),
      done: bubbleCanvas('done'),
    }),
    []
  )

  const placements = useMemo(() => stationPlacements(), [])

  const statusFor = useCallback(
    (station: StationPlacement) => stationStatus(station.phase, engagement),
    [engagement]
  )

  // Keep the latest engagement available to the loop without restarting it.
  const statusRef = useRef(statusFor)
  statusRef.current = statusFor

  const onEnterRef = useRef(onEnter)
  onEnterRef.current = onEnter
  const onFocusChangeRef = useRef(onFocusChange)
  onFocusChangeRef.current = onFocusChange

  const spriteFor = useCallback(
    (player: PlayerState): HTMLCanvasElement => {
      const frame = reducedMotion ? 0 : Math.floor(player.frame) % 4
      const key = `${player.facing}-${frame}-${avatar.hair}${avatar.suit}${avatar.skin}`
      return spriteCache.get(key, () => {
        const pose = playerPose(player.facing, frame)
        return rasterise(key, avatarSprite(pose.sprite, avatar), { flipX: pose.flipX })
      })
    },
    [avatar, reducedMotion, spriteCache]
  )

  const sarahSprite = useCallback(
    (mood: SarahMood): HTMLCanvasElement =>
      spriteCache.get(`sarah-${mood}`, () => rasterise(`sarah-${mood}`, SARAH_MOODS[mood])),
    [spriteCache]
  )

  // Teleport when the caller supplies an explicit start tile (dev preview only).
  useEffect(() => {
    if (!spawnAt) return
    playerRef.current = {
      ...playerRef.current,
      x: spawnAt.tileX * TILE + TILE / 2,
      y: spawnAt.tileY * TILE + TILE,
      frame: 0,
      moving: false,
    }
  }, [spawnAt])

  const interact = useCallback(() => {
    const near = nearestStation(playerRef.current)
    if (!near) return
    const status = statusRef.current(near.station)
    if (status === 'locked') {
      audio.play('deny')
      return
    }
    audio.play('enter')
    markVisited(near.station.glyph)
    onEnterRef.current(near.station)
  }, [markVisited])

  // ── Input ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    const down = (event: KeyboardEvent) => {
      // Never hijack typing.
      const target = event.target as HTMLElement | null
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return
      }
      if (isMoveKey(event.key)) {
        event.preventDefault()
        heldRef.current.add(event.key)
        audio.resume()
      } else if (INTERACT_KEYS.has(event.key)) {
        event.preventDefault()
        audio.resume()
        interact()
      }
    }
    const up = (event: KeyboardEvent) => {
      heldRef.current.delete(event.key)
    }
    const blur = () => heldRef.current.clear()

    window.addEventListener('keydown', down)
    window.addEventListener('keyup', up)
    window.addEventListener('blur', blur)
    return () => {
      window.removeEventListener('keydown', down)
      window.removeEventListener('keyup', up)
      window.removeEventListener('blur', blur)
    }
  }, [interact])

  // ── Frame loop ─────────────────────────────────────────────────────────────
  useEffect(() => {
    const canvas = canvasRef.current
    const wrapper = wrapperRef.current
    if (!canvas || !wrapper) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const dpr = Math.min(2, window.devicePixelRatio || 1)

    const resize = () => {
      const rect = wrapper.getBoundingClientRect()
      canvas.width = Math.max(1, Math.floor(rect.width * dpr))
      canvas.height = Math.max(1, Math.floor(rect.height * dpr))
      canvas.style.width = `${rect.width}px`
      canvas.style.height = `${rect.height}px`
    }
    const render = () => {
      const player = playerRef.current
      const rect = { width: canvas.width / dpr, height: canvas.height / dpr }
      const camera = cameraFor(player, rect.width, rect.height)

      // Station overheads.
      const actors: ActorDraw[] = []
      for (const placement of placements) {
        const status = statusRef.current(placement)
        const kind: BubbleKind | null =
          status === 'locked' ? 'locked' : status === 'done' ? 'done' : 'available'
        if (!kind) continue
        actors.push({
          canvas: bubbles[kind],
          x: placement.tileX * TILE + TILE / 2,
          y: placement.tileY * TILE - 2,
        })
      }

      // The persona only appears once the engagement has reached her.
      if (engagement) {
        actors.push({
          canvas: sarahSprite(sarahMood),
          x: SARAH_POSITION.tileX * TILE + TILE / 2,
          y: SARAH_POSITION.tileY * TILE + TILE,
        })
      }

      actors.push({ canvas: spriteFor(player), x: player.x, y: player.y })

      drawFrame(ctx, mapCanvas, camera, actors, dpr)

      // Proximity prompt — pushed to React only when it actually changes.
      const near = nearestStation(player)
      const nextKey = near ? near.station.glyph : null
      if (nextKey !== focusedRef.current) {
        focusedRef.current = nextKey
        const status = near ? statusRef.current(near.station) : null
        setPrompt(near ? { station: near.station, locked: status === 'locked' } : null)
        onFocusChangeRef.current?.(near?.station ?? null, status ?? 'locked')
      }
    }

    resize()
    // Paint immediately rather than waiting for the first animation frame. A
    // background or throttled tab may not schedule one at all, and an empty
    // black canvas on return looks like a crash.
    render()
    const observer = new ResizeObserver(() => {
      resize()
      render()
    })
    observer.observe(wrapper)

    const loop = (time: number) => {
      const dt = lastTimeRef.current === 0 ? 0 : Math.min(0.05, (time - lastTimeRef.current) / 1000)
      lastTimeRef.current = time

      const axis = axisFromKeys(heldRef.current)
      const player = step(playerRef.current, axis, dt)
      playerRef.current = player

      // Footstep cue, throttled to the walk cycle rather than the frame rate.
      if (player.moving) {
        stepSoundRef.current += dt
        if (stepSoundRef.current > 0.3) {
          stepSoundRef.current = 0
          audio.play('step')
        }
      } else {
        stepSoundRef.current = 0.3
      }

      render()
      rafRef.current = window.requestAnimationFrame(loop)
    }

    rafRef.current = window.requestAnimationFrame(loop)
    return () => {
      observer.disconnect()
      if (rafRef.current !== null) window.cancelAnimationFrame(rafRef.current)
      rafRef.current = null
      lastTimeRef.current = 0
    }
  }, [bubbles, engagement, mapCanvas, placements, sarahMood, sarahSprite, spawnAt, spriteFor])

  return (
    <div ref={wrapperRef} className={styles.world}>
      <canvas ref={canvasRef} className={styles.worldCanvas} aria-hidden="true" />

      {prompt && (
        <div className={`${styles.prompt} ${prompt.locked ? styles.promptLocked : ''}`}>
          <span className={styles.promptKey}>E</span>
          <span>
            {prompt.locked ? 'Locked — ' : 'Enter '}
            <strong>{prompt.station.title}</strong>
          </span>
        </div>
      )}

      {/* Screen-reader and keyboard-only equivalent of walking up to a station. */}
      <div className={styles.srOnly}>
        <h2>Office floor</h2>
        <p>
          Use the arrow keys or W A S D to walk, and E to enter a room. Every room is also listed as
          a button below the map.
        </p>
      </div>
    </div>
  )
}

/** Preview sprite used by the avatar picker during onboarding. */
export function avatarPreview(avatar: AvatarChoice, facing: 'down' | 'up' | 'right' = 'down') {
  const base = facing === 'up' ? PLAYER_UP : facing === 'right' ? PLAYER_RIGHT : PLAYER_DOWN
  return rasterise('avatar-preview', avatarSprite(base, avatar), { scale: 4 })
}
