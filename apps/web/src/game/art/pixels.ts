/**
 * A tiny string-art DSL for authoring pixel sprites in source.
 *
 * A sprite is an array of equal-length strings; each character is a key into
 * `PALETTE`. This keeps every asset in the repository as reviewable text — no
 * binary blobs, no licence questions, and a diff that shows exactly which
 * pixels changed.
 *
 * Sprites are rasterised once into offscreen canvases at load time and then
 * blitted, so the per-frame cost is a `drawImage` rather than thousands of
 * `fillRect` calls.
 */
import { colourFor } from './palette'

export type Sprite = readonly string[]

export interface RasterOptions {
  /** Integer upscale applied at raster time. Keep at 1 and scale the canvas instead. */
  scale?: number
  /** Horizontally mirror the sprite (used to derive "walk left" from "walk right"). */
  flipX?: boolean
}

export class SpriteError extends Error {}

/**
 * Validates that a sprite is rectangular and uses only known palette keys.
 * Called by `rasterise`, and exercised directly by the art unit tests so that a
 * miscounted row fails the build rather than silently rendering a torn sprite.
 */
export function assertRectangular(name: string, sprite: Sprite): void {
  if (sprite.length === 0) throw new SpriteError(`Sprite "${name}" is empty`)
  const width = sprite[0].length
  if (width === 0) throw new SpriteError(`Sprite "${name}" has zero-width rows`)
  sprite.forEach((row, y) => {
    if (row.length !== width) {
      throw new SpriteError(
        `Sprite "${name}" row ${y} has width ${row.length}, expected ${width}`
      )
    }
  })
}

export function spriteSize(sprite: Sprite): { width: number; height: number } {
  return { width: sprite[0]?.length ?? 0, height: sprite.length }
}

/**
 * Rasterises string art into an offscreen canvas.
 *
 * Returns an `HTMLCanvasElement` rather than an `ImageBitmap` because canvases
 * are synchronously usable, work in jsdom under test, and can be re-read for
 * recolouring.
 */
export function rasterise(
  name: string,
  sprite: Sprite,
  options: RasterOptions = {}
): HTMLCanvasElement {
  assertRectangular(name, sprite)
  const { scale = 1, flipX = false } = options
  const { width, height } = spriteSize(sprite)

  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new SpriteError(`Could not acquire 2D context for sprite "${name}"`)
  ctx.imageSmoothingEnabled = false

  for (let y = 0; y < height; y += 1) {
    const row = sprite[y]
    for (let x = 0; x < width; x += 1) {
      const key = row[flipX ? width - 1 - x : x]
      if (key === '.') continue
      ctx.fillStyle = colourFor(key)
      ctx.fillRect(x * scale, y * scale, scale, scale)
    }
  }
  return canvas
}

/**
 * Replaces palette keys before rasterising. Used to derive NPC variants (a
 * different suit or skin tone) from a single authored body, which keeps the
 * cast visually consistent without duplicating pixel data.
 */
export function recolour(sprite: Sprite, mapping: Record<string, string>): Sprite {
  return sprite.map((row) =>
    row
      .split('')
      .map((ch) => mapping[ch] ?? ch)
      .join('')
  )
}

/**
 * Vertically offsets a slice of rows by one pixel — the cheap trick that turns a
 * single standing pose into a believable walk cycle without authoring extra
 * frames. `from`/`to` bound the leg region.
 */
export function bobLegs(sprite: Sprite, from: number, to: number, direction: 1 | -1): Sprite {
  const out = [...sprite]
  const width = sprite[0].length
  const blank = '.'.repeat(width)
  const slice = sprite.slice(from, to)
  if (direction === 1) {
    out.splice(from, slice.length, blank, ...slice.slice(0, -1))
  } else {
    out.splice(from, slice.length, ...slice.slice(1), blank)
  }
  return out
}

/** Lazily rasterises and memoises a sprite by cache key. */
export function createSpriteCache() {
  const cache = new Map<string, HTMLCanvasElement>()
  return {
    get(key: string, build: () => HTMLCanvasElement): HTMLCanvasElement {
      const hit = cache.get(key)
      if (hit) return hit
      const made = build()
      cache.set(key, made)
      return made
    },
    clear() {
      cache.clear()
    },
    get size() {
      return cache.size
    },
  }
}
