/**
 * Rendering for the hub world.
 *
 * The static floor is composited **once** into a single 640x416 offscreen
 * canvas (40x26 tiles at 16px). Each frame then costs one `drawImage` of the
 * visible region plus a handful of actor blits, which keeps the loop cheap
 * enough to leave running underneath the Carbon work surfaces without competing
 * for main-thread time.
 */
import { HUB_MAP, MAP_HEIGHT, MAP_WIDTH, STATION_BY_GLYPH } from './map'
import { PROPS, TILE, drawSurface, type SurfaceKind } from '../art/tiles'
import { colourFor } from '../art/palette'
import { rasterise, recolour, type Sprite } from '../art/pixels'

export const MAP_PIXEL_WIDTH = MAP_WIDTH * TILE
export const MAP_PIXEL_HEIGHT = MAP_HEIGHT * TILE

/** Which procedural surface sits under each terrain character. */
const SURFACE_FOR: Record<string, SurfaceKind> = {
  '#': 'wall',
  w: 'wall',
  ',': 'polished',
  '.': 'carpet',
  '+': 'polished',
}

/** Props drawn on top of a floor tile, and the floor they sit on. */
const PROP_FOR: Record<string, { prop: keyof typeof PROPS; floor: SurfaceKind }> = {
  w: { prop: 'window', floor: 'wall' },
  t: { prop: 'plant', floor: 'carpet' },
  d: { prop: 'desk', floor: 'carpet' },
  h: { prop: 'chair', floor: 'carpet' },
  e: { prop: 'shelf', floor: 'carpet' },
  b: { prop: 'whiteboard', floor: 'carpet' },
  o: { prop: 'cooler', floor: 'carpet' },
  f: { prop: 'sofa', floor: 'carpet' },
  y: { prop: 'table', floor: 'polished' },
  q: { prop: 'server', floor: 'carpet' },
  '+': { prop: 'door', floor: 'polished' },
}

/** Accent palette keys for each station colour, used to tint the floor pad. */
const ACCENT_KEYS: Record<string, { outer: string; inner: string }> = {
  blue: { outer: 'B', inner: 'C' },
  teal: { outer: 'M', inner: 'q' },
  purple: { outer: 'P', inner: 'u' },
  green: { outer: 'v', inner: 'V' },
  grey: { outer: 'G', inner: 'N' },
}

function padSprite(accent: string): Sprite {
  const keys = ACCENT_KEYS[accent] ?? ACCENT_KEYS.blue
  return recolour(PROPS.stationPad, { B: keys.outer, C: keys.inner })
}

/**
 * Composites the whole floor plan into one canvas.
 *
 * Locked stations are drawn desaturated by the caller re-tinting on top, so the
 * base canvas can stay cached for the lifetime of the session.
 */
export function buildMapCanvas(): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = MAP_PIXEL_WIDTH
  canvas.height = MAP_PIXEL_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Could not acquire 2D context for the map canvas')
  ctx.imageSmoothingEnabled = false

  // A scratch tile we reuse for the procedural surfaces.
  const scratch = document.createElement('canvas')
  scratch.width = TILE
  scratch.height = TILE
  const sctx = scratch.getContext('2d')
  if (!sctx) throw new Error('Could not acquire 2D context for the surface scratch tile')

  for (let y = 0; y < MAP_HEIGHT; y += 1) {
    for (let x = 0; x < MAP_WIDTH; x += 1) {
      const ch = HUB_MAP[y][x]
      const station = STATION_BY_GLYPH.get(ch)

      const surface: SurfaceKind = station
        ? 'carpet'
        : (SURFACE_FOR[ch] ?? PROP_FOR[ch]?.floor ?? 'carpet')

      drawSurface(sctx, surface, x, y)
      ctx.drawImage(scratch, x * TILE, y * TILE)

      if (station) {
        ctx.drawImage(rasterise(`pad-${station.accent}`, padSprite(station.accent)), x * TILE, y * TILE)
        continue
      }

      const prop = PROP_FOR[ch]
      if (prop) {
        ctx.drawImage(rasterise(`prop-${prop.prop}`, PROPS[prop.prop]), x * TILE, y * TILE)
      }
    }
  }

  // Wall tops: lighten the tile directly above every wall run so rooms read as
  // enclosed volumes rather than flat blocks from this top-down angle.
  ctx.globalAlpha = 0.35
  for (let y = 1; y < MAP_HEIGHT; y += 1) {
    for (let x = 0; x < MAP_WIDTH; x += 1) {
      if (HUB_MAP[y][x] === '#' && HUB_MAP[y - 1][x] !== '#') {
        ctx.fillStyle = colourFor('N')
        ctx.fillRect(x * TILE, y * TILE, TILE, 3)
      }
    }
  }
  ctx.globalAlpha = 1

  return canvas
}

// ─── Viewport maths ──────────────────────────────────────────────────────────

export interface Camera {
  /** Top-left of the viewport, in map pixels. */
  x: number
  y: number
  /** Integer upscale factor. */
  zoom: number
  /** Viewport size in CSS pixels. */
  viewWidth: number
  viewHeight: number
}

/**
 * Centres the camera on a point and clamps it inside the map. Clamping is what
 * stops the player walking into a void at the edges — the camera stops before
 * the wall does.
 */
export function centreCamera(camera: Camera, targetX: number, targetY: number): Camera {
  const visibleW = camera.viewWidth / camera.zoom
  const visibleH = camera.viewHeight / camera.zoom
  const maxX = Math.max(0, MAP_PIXEL_WIDTH - visibleW)
  const maxY = Math.max(0, MAP_PIXEL_HEIGHT - visibleH)
  return {
    ...camera,
    x: Math.min(maxX, Math.max(0, targetX - visibleW / 2)),
    y: Math.min(maxY, Math.max(0, targetY - visibleH / 2)),
  }
}

/** Picks the largest integer zoom that still shows a useful slice of the floor. */
export function fitZoom(viewWidth: number): number {
  // Aim to show roughly 18 tiles across; never below 2x or the art disappears.
  const ideal = viewWidth / (18 * TILE)
  return Math.max(2, Math.min(5, Math.floor(ideal) || 2))
}

// ─── Frame drawing ───────────────────────────────────────────────────────────

export interface ActorDraw {
  canvas: HTMLCanvasElement
  /** Map-pixel position of the actor's feet centre. */
  x: number
  y: number
  /** Drawn above the actor when set (e.g. "!" over an available station). */
  bubble?: HTMLCanvasElement | null
}

export function drawFrame(
  ctx: CanvasRenderingContext2D,
  mapCanvas: HTMLCanvasElement,
  camera: Camera,
  actors: ActorDraw[],
  dpr: number
): void {
  const { zoom } = camera
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.imageSmoothingEnabled = false
  ctx.fillStyle = colourFor('k')
  ctx.fillRect(0, 0, camera.viewWidth, camera.viewHeight)

  const visibleW = camera.viewWidth / zoom
  const visibleH = camera.viewHeight / zoom

  // Snap the source to whole pixels so the tile art never samples half a texel.
  const sx = Math.round(camera.x)
  const sy = Math.round(camera.y)

  ctx.drawImage(
    mapCanvas,
    sx,
    sy,
    Math.ceil(visibleW),
    Math.ceil(visibleH),
    0,
    0,
    Math.ceil(visibleW) * zoom,
    Math.ceil(visibleH) * zoom
  )

  // Painter's algorithm: sort by feet Y so an actor lower on screen overlaps one
  // standing behind them.
  const ordered = [...actors].sort((a, b) => a.y - b.y)
  for (const actor of ordered) {
    const dx = Math.round(actor.x - sx - actor.canvas.width / 2) * zoom
    const dy = Math.round(actor.y - sy - actor.canvas.height) * zoom

    // Contact shadow — a flat ellipse is enough to stop actors looking pasted on.
    ctx.globalAlpha = 0.28
    ctx.fillStyle = '#000000'
    ctx.beginPath()
    ctx.ellipse(
      dx + (actor.canvas.width * zoom) / 2,
      dy + actor.canvas.height * zoom - 2 * zoom,
      5 * zoom,
      2 * zoom,
      0,
      0,
      Math.PI * 2
    )
    ctx.fill()
    ctx.globalAlpha = 1

    ctx.drawImage(actor.canvas, dx, dy, actor.canvas.width * zoom, actor.canvas.height * zoom)

    if (actor.bubble) {
      ctx.drawImage(
        actor.bubble,
        dx + (actor.canvas.width * zoom) / 2 - (actor.bubble.width * zoom) / 2,
        dy - (actor.bubble.height + 2) * zoom,
        actor.bubble.width * zoom,
        actor.bubble.height * zoom
      )
    }
  }
}

// ─── Overhead indicators ─────────────────────────────────────────────────────

const BUBBLE_AVAILABLE: Sprite = [
  '.kkkkkkk.',
  'kAAAAAAAk',
  'kAAABAAAk',
  'kAAABAAAk',
  'kAAABAAAk',
  'kAAAAAAAk',
  'kAAABAAAk',
  'kAAAAAAAk',
  '.kkkBkkk.',
  '....B....',
]

const BUBBLE_LOCKED: Sprite = [
  '.kkkkkkk.',
  'kNNNNNNNk',
  'kNNkkkNNk',
  'kNkNNNkNk',
  'kNkNNNkNk',
  'kkkkkkkkk',
  'kkGGGGGkk',
  'kkGGkGGkk',
  'kkGGGGGkk',
  '.kkkkkkk.',
]

const BUBBLE_DONE: Sprite = [
  '.kkkkkkk.',
  'kVVVVVVVk',
  'kVVVVVvVk',
  'kVVVVvVVk',
  'kVvVvVVVk',
  'kVVvvVVVk',
  'kVVVvVVVk',
  'kVVVVVVVk',
  '.kkkkkkk.',
  '.........',
]

export type BubbleKind = 'available' | 'locked' | 'done'

const BUBBLES: Record<BubbleKind, Sprite> = {
  available: BUBBLE_AVAILABLE,
  locked: BUBBLE_LOCKED,
  done: BUBBLE_DONE,
}

export function bubbleCanvas(kind: BubbleKind): HTMLCanvasElement {
  return rasterise(`bubble-${kind}`, BUBBLES[kind])
}
