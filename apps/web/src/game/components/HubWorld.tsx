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
  activeStation,
  axisFromKeys,
  cameraFor,
  followPath,
  initialPlayer,
  INTERACT_KEYS,
  isMoveKey,
  playerTile,
  stationPlacements,
  step,
  tileFromCanvasPoint,
  type PlayerState,
} from '../world/engine'
import { findPath, nearestWalkable, type TilePoint } from '../world/pathfinding'
import { SARAH_POSITION } from '../world/map'
import { roomAtTile } from '../world/rooms'
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

/** Plain-language status shown under each room name. */
const PLATE_STATUS: Record<ReturnType<typeof stationStatus>, string> = {
  current: 'Go here next',
  done: 'Done',
  locked: 'Locked',
  open: 'Open any time',
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
  const pathRef = useRef<TilePoint[]>([])
  const stuckRef = useRef<number>(0)
  const cameraRef = useRef<ReturnType<typeof cameraFor> | null>(null)
  const plateRefs = useRef<Map<string, HTMLDivElement>>(new Map())

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

  /** Where each room's name plate hangs, in tile coordinates. */
  const plateAnchors = useMemo(() => {
    const anchors = new Map<string, { x: number; y: number }>()
    for (const placement of placements) {
      const room = roomAtTile(placement.tileX, placement.tileY)
      anchors.set(
        placement.glyph,
        room
          ? { x: room.labelX, y: room.labelY }
          : { x: placement.tileX + 0.5, y: placement.tileY }
      )
    }
    return anchors
  }, [placements])

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
    const station = activeStation(playerRef.current)
    if (!station) return
    const status = statusRef.current(station)
    if (status === 'locked') {
      audio.play('deny')
      return
    }
    audio.play('enter')
    markVisited(station.glyph)
    onEnterRef.current(station)
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
      cameraRef.current = camera

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

      // Move the room name plates with the camera. Done by writing transforms
      // directly rather than through state, so this costs nothing per frame.
      for (const placement of placements) {
        const plate = plateRefs.current.get(placement.glyph)
        if (!plate) continue
        const anchor = plateAnchors.get(placement.glyph)
        if (!anchor) continue
        const px = (anchor.x * TILE - camera.x) * camera.zoom
        const py = (anchor.y * TILE - camera.y) * camera.zoom
        const visible =
          px > -140 && px < rect.width + 140 && py > -60 && py < rect.height + 60
        plate.style.display = visible ? 'flex' : 'none'
        if (!visible) continue

        // Keep the plate inside the viewport. On a small screen a room's top
        // row is often just off the edge, and a half-clipped label that reads
        // "DONE" with no room name is worse than useless.
        const halfWidth = plate.offsetWidth / 2
        const clampedX = Math.min(
          Math.max(px, halfWidth + 4),
          Math.max(halfWidth + 4, rect.width - halfWidth - 4)
        )
        const clampedY = Math.min(
          Math.max(py, 4),
          Math.max(4, rect.height - plate.offsetHeight - 4)
        )
        plate.style.transform = `translate(${Math.round(clampedX)}px, ${Math.round(clampedY)}px) translate(-50%, 0)`
      }

      // Which station is actionable — room membership, not pad proximity.
      // Pushed to React only when it actually changes.
      const station = activeStation(player)
      const nextKey = station ? station.glyph : null
      if (nextKey !== focusedRef.current) {
        focusedRef.current = nextKey
        const status = station ? statusRef.current(station) : null
        setPrompt(station ? { station, locked: status === 'locked' } : null)
        onFocusChangeRef.current?.(station ?? null, status ?? 'locked')
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

      // Keyboard always wins: touching a movement key abandons a clicked route
      // rather than fighting it.
      const keyAxis = axisFromKeys(heldRef.current)
      let axis = keyAxis
      if (keyAxis.x !== 0 || keyAxis.y !== 0) {
        pathRef.current = []
      } else if (pathRef.current.length > 0) {
        const followed = followPath(playerRef.current, pathRef.current)
        pathRef.current = followed.remaining as TilePoint[]
        axis = followed.axis
      }

      const player = step(playerRef.current, axis, dt)

      // A clicked route that stops making progress (blocked by a chair that was
      // not there when the path was planned) is dropped rather than looping.
      if (pathRef.current.length > 0 && player.x === playerRef.current.x && player.y === playerRef.current.y) {
        stuckRef.current += dt
        if (stuckRef.current > 0.35) {
          pathRef.current = []
          stuckRef.current = 0
        }
      } else {
        stuckRef.current = 0
      }

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

  /** Click anywhere walkable to route the avatar there. */
  const handleClick = useCallback((event: React.MouseEvent<HTMLCanvasElement>) => {
    const camera = cameraRef.current
    const canvas = canvasRef.current
    if (!camera || !canvas) return

    audio.resume()
    const rect = canvas.getBoundingClientRect()
    const clicked = tileFromCanvasPoint(event.clientX, event.clientY, rect, camera)
    const destination = nearestWalkable(clicked)
    if (!destination) {
      audio.play('deny')
      return
    }

    const path = findPath(playerTile(playerRef.current), destination)
    if (path.length === 0) return
    pathRef.current = path
    stuckRef.current = 0
    audio.play('blip')
  }, [])

  return (
    <div ref={wrapperRef} className={styles.world}>
      <canvas
        ref={canvasRef}
        className={styles.worldCanvas}
        onClick={handleClick}
        aria-hidden="true"
      />

      {/* Room name plates. Without these the floor is a set of anonymous boxes
          and the learner has no way to tell where anything is. */}
      <div className={styles.plates} aria-hidden="true">
        {placements.map((placement) => {
          const status = statusFor(placement)
          const cls =
            status === 'current'
              ? styles.plateCurrent
              : status === 'done'
                ? styles.plateDone
                : status === 'open'
                  ? styles.plateOpen
                  : styles.plateLocked
          return (
            <div
              key={placement.glyph}
              ref={(node) => {
                if (node) plateRefs.current.set(placement.glyph, node)
                else plateRefs.current.delete(placement.glyph)
              }}
              className={`${styles.plate} ${cls}`}
            >
              <span className={styles.plateName}>{placement.title}</span>
              <span className={styles.plateStatus}>{PLATE_STATUS[status]}</span>
              {status === 'current' && <span className={styles.plateBeacon} />}
            </div>
          )
        })}
      </div>

      {prompt && (
        <div className={`${styles.prompt} ${prompt.locked ? styles.promptLocked : ''}`}>
          <span className={styles.promptKey}>E</span>
          <span>
            {prompt.locked ? 'Locked — ' : 'Enter '}
            <strong>{prompt.station.title}</strong>
          </span>
        </div>
      )}

      {/* Touch controls. Shown only on coarse pointers, because a phone has no
          arrow keys and tap-to-move alone cannot nudge you around a desk. The
          buttons drive the same held-key set as a keyboard, so there is one
          movement path to reason about. */}
      <div className={styles.touchPad} aria-hidden="true">
        {(
          [
            ['up', 'ArrowUp', '▲', styles.padUp],
            ['left', 'ArrowLeft', '◀', styles.padLeft],
            ['right', 'ArrowRight', '▶', styles.padRight],
            ['down', 'ArrowDown', '▼', styles.padDown],
          ] as const
        ).map(([name, key, glyph, cls]) => (
          <button
            key={name}
            type="button"
            className={`${styles.padButton} ${cls}`}
            onPointerDown={(event) => {
              event.preventDefault()
              audio.resume()
              pathRef.current = []
              heldRef.current.add(key)
            }}
            onPointerUp={() => heldRef.current.delete(key)}
            onPointerLeave={() => heldRef.current.delete(key)}
            onPointerCancel={() => heldRef.current.delete(key)}
            tabIndex={-1}
          >
            {glyph}
          </button>
        ))}
      </div>

      <button
        type="button"
        className={styles.touchEnter}
        onClick={() => {
          audio.resume()
          interact()
        }}
        disabled={!prompt || prompt.locked}
      >
        {prompt ? `Enter ${prompt.station.title}` : 'Walk to a room'}
      </button>

      {/* Screen-reader and keyboard-only equivalent of walking up to a station. */}
      <div className={styles.srOnly}>
        <h2>Office floor</h2>
        <p>
          Walk with the arrow keys or W A S D, or click where you want to go. Press E anywhere
          inside a room to enter it. Every room is also listed as a button below the map.
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
