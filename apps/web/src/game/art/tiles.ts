/**
 * The office tileset.
 *
 * Split deliberately in two:
 *
 *  - Floors and walls are generated procedurally, because what they need is
 *    subtle per-tile noise so a large room does not read as wallpaper.
 *  - Props are authored as string art, because their shapes carry meaning and a
 *    reviewer should be able to see a desk in the diff.
 *
 * All tiles are 16x16 so the tilemap can index them by a single character.
 */
import type { Sprite } from './pixels'
import { colourFor } from './palette'

export const TILE = 16

// ─── Procedural surfaces ─────────────────────────────────────────────────────

/**
 * Deterministic hash-based noise. A seeded value per pixel means the same tile
 * index always renders identically across reloads (important: a floor that
 * shimmers between renders looks broken).
 */
function noise(x: number, y: number, seed: number): number {
  let h = x * 374761393 + y * 668265263 + seed * 1442695040888963407
  h = (h ^ (h >> 13)) * 1274126177
  return ((h ^ (h >> 16)) >>> 0) / 4294967295
}

export type SurfaceKind = 'carpet' | 'polished' | 'wall' | 'wallTop' | 'grass' | 'road'

interface SurfaceSpec {
  base: string
  speckA: string
  speckB: string
  density: number
  /** Draws a grout line every N pixels; 0 disables. */
  grid: number
  gridColour?: string
}

const SURFACES: Record<SurfaceKind, SurfaceSpec> = {
  // Corporate low-pile carpet — the dominant floor of the hub. Deliberately a
  // mid grey: dark enough to sit below the lobby stone in the visual hierarchy,
  // light enough that the rooms do not read as unlit.
  carpet: { base: 'D', speckA: 'g', speckB: 'd', density: 0.3, grid: 0 },
  // Lobby / meeting-room stone.
  polished: { base: 'l', speckA: 'L', speckB: 'N', density: 0.16, grid: 8, gridColour: 'N' },
  // Painted partition wall — the darkest surface, so walls always read as walls.
  wall: { base: 'K', speckA: 'd', speckB: 'k', density: 0.12, grid: 0 },
  // Wall cap, lighter so rooms read as enclosed from above.
  wallTop: { base: 'N', speckA: 'l', speckB: 'G', density: 0.12, grid: 0 },
  // Courtyard planting, visible through the atrium windows.
  grass: { base: 'E', speckA: 'v', speckB: 'e', density: 0.4, grid: 0 },
  // Access road / hard standing outside the building.
  road: { base: 'd', speckA: 'D', speckB: 'K', density: 0.2, grid: 0 },
}

export function drawSurface(
  ctx: CanvasRenderingContext2D,
  kind: SurfaceKind,
  tileX: number,
  tileY: number
): void {
  const spec = SURFACES[kind]
  ctx.fillStyle = colourFor(spec.base)
  ctx.fillRect(0, 0, TILE, TILE)

  for (let y = 0; y < TILE; y += 1) {
    for (let x = 0; x < TILE; x += 1) {
      const n = noise(tileX * TILE + x, tileY * TILE + y, 1)
      if (n > 1 - spec.density) {
        ctx.fillStyle = colourFor(n > 1 - spec.density / 3 ? spec.speckB : spec.speckA)
        ctx.fillRect(x, y, 1, 1)
      }
    }
  }

  if (spec.grid > 0) {
    ctx.fillStyle = colourFor(spec.gridColour ?? 'N')
    for (let i = 0; i < TILE; i += spec.grid) {
      ctx.fillRect(0, i, TILE, 1)
      ctx.fillRect(i, 0, 1, TILE)
    }
  }
}

// ─── Authored props ──────────────────────────────────────────────────────────

export const PROP_DESK: Sprite = [
  '................',
  '................',
  '....kkkkkkkk....',
  '....kKBBBBKk....',
  '....kKBaaBKk....',
  '....kKBBBBKk....',
  '....kkkkkkkk....',
  '.....kddddk.....',
  'kkkkkkkkkkkkkkkk',
  'kNNNNNNNNNNNNNNk',
  'kNlllllllllllNNk',
  'kNNNNNNNNNNNNNNk',
  'kkkkkkkkkkkkkkkk',
  '.kd..........dk.',
  '.kd..........dk.',
  '.kk..........kk.',
]

export const PROP_CHAIR: Sprite = [
  '................',
  '.....kkkkkk.....',
  '....kKKKKKKk....',
  '....kKKKKKKk....',
  '....kKKKKKKk....',
  '....kKKKKKKk....',
  '.....kkkkkk.....',
  '...kkkkkkkkkk...',
  '...kKKKKKKKKk...',
  '...kKKKKKKKKk...',
  '...kkkkkkkkkk...',
  '......kddk......',
  '......kddk......',
  '.....kkddkk.....',
  '....kd....dk....',
  '....kk....kk....',
]

export const PROP_PLANT: Sprite = [
  '................',
  '.......vv.......',
  '....vvvVVvvv....',
  '...vVVvvvvVVv...',
  '..vVvv.vv.vvVv..',
  '..vVv.vVVv.vVv..',
  '...vv.vVVv.vv...',
  '.....v.vv.v.....',
  '.......ee.......',
  '.......ee.......',
  '......keek......',
  '.....kRRRRk.....',
  '.....kRoooRk....',
  '.....kRRRRRk....',
  '.....kRRRRRk....',
  '.....kkkkkkk....',
]

export const PROP_WINDOW: Sprite = [
  'kkkkkkkkkkkkkkkk',
  'kNNNNNNNNNNNNNNk',
  'kNaaaaaakaaaaaNk',
  'kNaAAAAakaAAAANk',
  'kNaAAAAakaAAAANk',
  'kNaaaaaakaaaaaNk',
  'kNkkkkkkkkkkkkNk',
  'kNaaaaaakaaaaaNk',
  'kNaAAAAakaAAAANk',
  'kNaAAAAakaAAAANk',
  'kNaaaaaakaaaaaNk',
  'kNNNNNNNNNNNNNNk',
  'kkkkkkkkkkkkkkkk',
  '.kddddddddddddk.',
  '.kddddddddddddk.',
  '.kkkkkkkkkkkkkk.',
]

export const PROP_DOOR: Sprite = [
  'kkkkkkkkkkkkkkkk',
  'kddddddddddddddk',
  'kdRRRRRRRRRRRRdk',
  'kdRoooooooooRRdk',
  'kdRoRRRRRRoORRdk',
  'kdRoRRRRRRoORRdk',
  'kdRoRRRRRRoORRdk',
  'kdRoooooooooRRdk',
  'kdRRRRRRRRRRRRdk',
  'kdRRRRRRRRRRRRdk',
  'kdRRRRROORRRRRdk',
  'kdRRRRROORRRRRdk',
  'kdRRRRRRRRRRRRdk',
  'kdRRRRRRRRRRRRdk',
  'kddddddddddddddk',
  'kkkkkkkkkkkkkkkk',
]

export const PROP_WHITEBOARD: Sprite = [
  'kkkkkkkkkkkkkkkk',
  'kLLLLLLLLLLLLLLk',
  'kLwwwwwwwwwwwwLk',
  'kLwBBBBwwwwwwwLk',
  'kLwwwwwwwyywwwLk',
  'kLwwBBBBwwwwwwLk',
  'kLwwwwwwwwvvwwLk',
  'kLwBBwwwwwwwwwLk',
  'kLwwwwwwwwwwwwLk',
  'kLwwwwBBBBwwwwLk',
  'kLwwwwwwwwwwwwLk',
  'kLLLLLLLLLLLLLLk',
  'kkkkkkkkkkkkkkkk',
  '...kd......dk...',
  '...kd......dk...',
  '...kk......kk...',
]

export const PROP_SERVER: Sprite = [
  'kkkkkkkkkkkkkkkk',
  'kKKKKKKKKKKKKKKk',
  'kKdddddddddddKKk',
  'kKdvKKKKKKKKdKKk',
  'kKdddddddddddKKk',
  'kKdvKKKKKKKKdKKk',
  'kKdddddddddddKKk',
  'kKdOKKKKKKKKdKKk',
  'kKdddddddddddKKk',
  'kKdvKKKKKKKKdKKk',
  'kKdddddddddddKKk',
  'kKdyKKKKKKKKdKKk',
  'kKdddddddddddKKk',
  'kKKKKKKKKKKKKKKk',
  'kkkkkkkkkkkkkkkk',
  '.kk..........kk.',
]

export const PROP_COOLER: Sprite = [
  '................',
  '.....kkkkkk.....',
  '....kaaaaaak....',
  '....kaAAAAak....',
  '....kaAAAAak....',
  '....kaaaaaak....',
  '.....kkkkkk.....',
  '....kLLLLLLk....',
  '....kLwwwwLk....',
  '....kLwBBwLk....',
  '....kLwwwwLk....',
  '....kLLLLLLk....',
  '....kNNNNNNk....',
  '....kNNNNNNk....',
  '....kkkkkkkk....',
  '................',
]

export const PROP_SOFA: Sprite = [
  '................',
  '................',
  'kkkkkkkkkkkkkkkk',
  'kbbbbbbbbbbbbbbk',
  'kbccccbbccccbbbk',
  'kbccccbbccccbbbk',
  'kbbbbbbbbbbbbbbk',
  'kkkkkkkkkkkkkkkk',
  'kbccccccccccccbk',
  'kbccccccccccccbk',
  'kbbbbbbbbbbbbbbk',
  'kkkkkkkkkkkkkkkk',
  '.kd..........dk.',
  '.kd..........dk.',
  '.kk..........kk.',
  '................',
]

export const PROP_TABLE: Sprite = [
  '................',
  '................',
  'kkkkkkkkkkkkkkkk',
  'kRRRRRRRRRRRRRRk',
  'kRoooooooooooRRk',
  'kRoRRRRRRRRRoRRk',
  'kRoRRRRRRRRRoRRk',
  'kRoooooooooooRRk',
  'kRRRRRRRRRRRRRRk',
  'kkkkkkkkkkkkkkkk',
  '..kd........dk..',
  '..kd........dk..',
  '..kd........dk..',
  '..kk........kk..',
  '................',
  '................',
]

export const PROP_SHELF: Sprite = [
  'kkkkkkkkkkkkkkkk',
  'kRRRRRRRRRRRRRRk',
  'kRyybbvvOOyybbRk',
  'kRyybbvvOOyybbRk',
  'kRRRRRRRRRRRRRRk',
  'kRbbOOyyvvbbOORk',
  'kRbbOOyyvvbbOORk',
  'kRRRRRRRRRRRRRRk',
  'kRvvyybbOOvvyyRk',
  'kRvvyybbOOvvyyRk',
  'kRRRRRRRRRRRRRRk',
  'kROOvvyybbOOvvRk',
  'kROOvvyybbOOvvRk',
  'kRRRRRRRRRRRRRRk',
  'kkkkkkkkkkkkkkkk',
  '................',
]

/** A floor decal marking an interactive station. Drawn under the prop. */
export const PROP_STATION_PAD: Sprite = [
  '................',
  '..BBBBBBBBBBBB..',
  '.B............B.',
  'B..CCCCCCCCCC..B',
  'B..C........C..B',
  'B..C........C..B',
  'B..C........C..B',
  'B..C........C..B',
  'B..C........C..B',
  'B..C........C..B',
  'B..C........C..B',
  'B..CCCCCCCCCC..B',
  '.B............B.',
  '..BBBBBBBBBBBB..',
  '................',
  '................',
]

export const PROPS: Record<string, Sprite> = {
  desk: PROP_DESK,
  chair: PROP_CHAIR,
  plant: PROP_PLANT,
  window: PROP_WINDOW,
  door: PROP_DOOR,
  whiteboard: PROP_WHITEBOARD,
  server: PROP_SERVER,
  cooler: PROP_COOLER,
  sofa: PROP_SOFA,
  table: PROP_TABLE,
  shelf: PROP_SHELF,
  stationPad: PROP_STATION_PAD,
}

export type PropKey = keyof typeof PROPS
