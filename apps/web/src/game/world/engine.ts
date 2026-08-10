/**
 * The hub world simulation loop: input, movement, collision, camera and station
 * proximity. Deliberately framework-free so it can be unit-tested without a DOM
 * renderer and so the React layer only has to own presentation.
 */
import { TILE } from '../art/tiles'
import { isWalkable, placeStations, SPAWN, type StationPlacement } from './map'
import { roomAtTile } from './rooms'
import { centreCamera, fitZoom, type Camera } from './renderer'
import type { Facing } from '../art/actors'
import type { TilePoint } from './pathfinding'

/** Walking speed in map pixels per second.
 *
 *  Raised from 62 after playtesting: at the old speed crossing the floor took
 *  long enough to feel like a toll on every navigation, which is exactly the
 *  friction the world was supposed to remove. */
export const WALK_SPEED = 96

/** Seconds per walk-cycle frame. */
const FRAME_TIME = 0.13

/** How close the player's feet must be to a station pad to interact, in pixels. */
export const INTERACT_RADIUS = 22

/** Collision box around the player's feet, in pixels. Narrower than the sprite
 *  so shoulders can overlap furniture — standard top-down RPG feel. */
const FOOT_HALF_WIDTH = 4
const FOOT_HEIGHT = 5

export interface PlayerState {
  /** Map-pixel position of the feet centre. */
  x: number
  y: number
  facing: Facing
  frame: number
  moving: boolean
}

export type InputAxis = { x: number; y: number }

export function initialPlayer(): PlayerState {
  return {
    x: SPAWN.tileX * TILE + TILE / 2,
    y: SPAWN.tileY * TILE + TILE,
    facing: 'down',
    frame: 0,
    moving: false,
  }
}

/**
 * True when the foot box at (x, y) sits entirely on walkable tiles.
 * Checked per-axis by `step` so sliding along a wall works instead of sticking.
 */
export function canStand(x: number, y: number): boolean {
  const left = x - FOOT_HALF_WIDTH
  const right = x + FOOT_HALF_WIDTH - 1
  const top = y - FOOT_HEIGHT
  const bottom = y - 1
  const corners: Array<[number, number]> = [
    [left, top],
    [right, top],
    [left, bottom],
    [right, bottom],
  ]
  return corners.every(([px, py]) => isWalkable(Math.floor(px / TILE), Math.floor(py / TILE)))
}

function facingFor(axis: InputAxis, current: Facing): Facing {
  // Vertical wins ties so diagonal input still yields a stable sprite.
  if (Math.abs(axis.y) > Math.abs(axis.x)) return axis.y > 0 ? 'down' : 'up'
  if (axis.x !== 0) return axis.x > 0 ? 'right' : 'left'
  return current
}

/**
 * Advances the player by one tick. Pure: takes state, returns state.
 *
 * Axes are resolved independently so that walking into a corner slides along the
 * wall rather than stopping dead — the single biggest difference between a world
 * that feels navigable and one that feels sticky.
 */
export function step(player: PlayerState, axis: InputAxis, dt: number): PlayerState {
  const magnitude = Math.hypot(axis.x, axis.y)
  if (magnitude === 0) {
    return { ...player, moving: false, frame: 0 }
  }

  const nx = axis.x / magnitude
  const ny = axis.y / magnitude
  const distance = WALK_SPEED * dt

  let { x, y } = player
  const tryX = x + nx * distance
  if (canStand(tryX, y)) x = tryX
  const tryY = y + ny * distance
  if (canStand(x, tryY)) y = tryY

  const moved = x !== player.x || y !== player.y
  const frame = moved ? player.frame + dt / FRAME_TIME : 0

  return {
    x,
    y,
    facing: facingFor(axis, player.facing),
    frame,
    moving: moved,
  }
}

// ─── Station proximity ───────────────────────────────────────────────────────

export interface NearbyStation {
  station: StationPlacement
  distance: number
}

const PLACEMENTS = placeStations()

export function stationPlacements(): StationPlacement[] {
  return PLACEMENTS
}

/** The closest station pad within `INTERACT_RADIUS` of the player's feet. */
export function nearestStation(player: PlayerState): NearbyStation | null {
  let best: NearbyStation | null = null
  for (const station of PLACEMENTS) {
    const cx = station.tileX * TILE + TILE / 2
    const cy = station.tileY * TILE + TILE / 2
    const distance = Math.hypot(player.x - cx, player.y - cy)
    if (distance <= INTERACT_RADIUS && (best === null || distance < best.distance)) {
      best = { station, distance }
    }
  }
  return best
}

/** The tile the player's feet are standing on. */
export function playerTile(player: PlayerState): { tileX: number; tileY: number } {
  return {
    tileX: Math.floor(player.x / TILE),
    tileY: Math.floor((player.y - 1) / TILE),
  }
}

/**
 * The station the player can currently act on.
 *
 * Room membership, not pad proximity: being anywhere inside the Research
 * Library is enough to enter it. Falls back to pad proximity so the lobby and
 * the central hall — which are one big room each — still resolve to their
 * station only when you are actually near its desk, rather than from forty
 * tiles away.
 */
export function activeStation(player: PlayerState): StationPlacement | null {
  const { tileX, tileY } = playerTile(player)
  const room = roomAtTile(tileX, tileY)
  if (!room || !room.station) return nearestStation(player)?.station ?? null

  const placement = PLACEMENTS.find((p) => p.glyph === room.station?.glyph) ?? null
  if (!placement) return null

  // Large open areas keep the proximity rule; enclosed rooms do not.
  if (room.tiles.size > LARGE_ROOM_TILES) {
    return nearestStation(player)?.station ?? null
  }
  return placement
}

/** Above this many tiles a region is treated as a hall rather than a room. */
export const LARGE_ROOM_TILES = 90

// ─── Path following ──────────────────────────────────────────────────────────

/** How close to a waypoint's centre counts as having reached it. */
const WAYPOINT_TOLERANCE = 2.5

/**
 * Converts the next waypoint into an input axis, so a clicked route and a held
 * key drive the exact same movement code — including collision and sliding.
 * Returns a zero axis and the shortened path once a waypoint is reached.
 */
export function followPath(
  player: PlayerState,
  path: readonly TilePoint[]
): { axis: InputAxis; remaining: readonly TilePoint[] } {
  if (path.length === 0) return { axis: { x: 0, y: 0 }, remaining: path }

  const target = path[0]
  const tx = target.tileX * TILE + TILE / 2
  const ty = target.tileY * TILE + TILE
  const dx = tx - player.x
  const dy = ty - player.y

  if (Math.hypot(dx, dy) <= WAYPOINT_TOLERANCE) {
    return { axis: { x: 0, y: 0 }, remaining: path.slice(1) }
  }

  // Normalise to a unit-ish axis; `step` re-normalises, so magnitude is free.
  return { axis: { x: Math.sign(dx) * Math.min(1, Math.abs(dx)), y: Math.sign(dy) * Math.min(1, Math.abs(dy)) }, remaining: path }
}

/** Converts a click in canvas CSS pixels into a map tile. */
export function tileFromCanvasPoint(
  clientX: number,
  clientY: number,
  rect: { left: number; top: number },
  camera: Camera
): TilePoint {
  const mapX = camera.x + (clientX - rect.left) / camera.zoom
  const mapY = camera.y + (clientY - rect.top) / camera.zoom
  return { tileX: Math.floor(mapX / TILE), tileY: Math.floor(mapY / TILE) }
}

// ─── Keyboard ────────────────────────────────────────────────────────────────

const MOVE_KEYS: Record<string, InputAxis> = {
  ArrowUp: { x: 0, y: -1 },
  ArrowDown: { x: 0, y: 1 },
  ArrowLeft: { x: -1, y: 0 },
  ArrowRight: { x: 1, y: 0 },
  w: { x: 0, y: -1 },
  s: { x: 0, y: 1 },
  a: { x: -1, y: 0 },
  d: { x: 1, y: 0 },
  W: { x: 0, y: -1 },
  S: { x: 0, y: 1 },
  A: { x: -1, y: 0 },
  D: { x: 1, y: 0 },
}

export const INTERACT_KEYS = new Set(['e', 'E', 'Enter', ' '])

export function isMoveKey(key: string): boolean {
  return key in MOVE_KEYS
}

/** Resolves the held key set into a single normalised direction. */
export function axisFromKeys(held: ReadonlySet<string>): InputAxis {
  let x = 0
  let y = 0
  for (const key of held) {
    const delta = MOVE_KEYS[key]
    if (!delta) continue
    x += delta.x
    y += delta.y
  }
  return { x: Math.max(-1, Math.min(1, x)), y: Math.max(-1, Math.min(1, y)) }
}

// ─── Camera helper ───────────────────────────────────────────────────────────

export function cameraFor(
  player: PlayerState,
  viewWidth: number,
  viewHeight: number,
  previous?: Camera
): Camera {
  const zoom = previous?.zoom ?? fitZoom(viewWidth, viewHeight)
  const base: Camera = { x: 0, y: 0, zoom, viewWidth, viewHeight }
  return centreCamera(base, player.x, player.y - TILE / 2)
}
