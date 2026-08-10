/**
 * Palette for the pixel layer.
 *
 * Every colour used by the world is declared here as a single character key so
 * that sprites can be authored as plain string art (see `pixels.ts`). Keeping
 * the palette small and shared is what makes independently-authored sprites and
 * tiles look like one coherent set rather than a collage.
 *
 * Hues are deliberately pulled towards the IBM Carbon Blue ramp so the pixel
 * world and the Carbon-styled work surfaces read as the same product.
 */
export type PaletteKey = string

export const PALETTE: Record<PaletteKey, string> = {
  // transparent
  '.': 'transparent',

  // neutrals / structure
  k: '#161616', // Carbon gray-100 — outlines
  K: '#262626', // gray-90
  d: '#393939', // gray-80
  D: '#525252', // gray-70
  g: '#6f6f6f', // gray-60
  G: '#8d8d8d', // gray-50
  n: '#a8a8a8', // gray-40
  N: '#c6c6c6', // gray-30
  l: '#e0e0e0', // gray-20
  L: '#f4f4f4', // gray-10
  w: '#ffffff',

  // IBM blue ramp
  b: '#0043ce', // blue-70
  B: '#0f62fe', // blue-60 — the interactive blue
  c: '#4589ff', // blue-50
  C: '#78a9ff', // blue-40
  a: '#d0e2ff', // blue-20
  A: '#edf5ff', // blue-10

  // warm woods / furniture
  r: '#8a3800', // brown dark
  R: '#ba4e00', // brown
  o: '#d4700a', // orange-ish wood highlight
  O: '#f1c21b', // yellow-30 (lamp glow, highlights)

  // greens (plants, success)
  e: '#044317',
  E: '#0e6027',
  v: '#24a148',
  V: '#42be65',

  // reds (alerts, ties, rejection)
  x: '#750e13',
  X: '#a2191f',
  y: '#da1e28',
  Y: '#fa4d56',

  // skin tones (kept deliberately varied across NPCs)
  s: '#f1c9a5',
  S: '#d9a679',
  t: '#a86f45',
  T: '#7a4a28',

  // hair
  h: '#2c1810',
  H: '#4a2c17',
  z: '#6b4423',
  Z: '#c9a227',

  // teal / accent for the "meeting" zone
  m: '#005d5d',
  M: '#009d9a',
  q: '#3ddbd9',

  // purple accent (AI review zone)
  p: '#491d8b',
  P: '#8a3ffc',
  u: '#be95ff',
} as const

/** Fallback for an unknown palette key — hot pink, so mistakes are obvious. */
export const MISSING_COLOUR = '#ff00ff'

export function colourFor(key: PaletteKey): string {
  if (key === '.') return 'transparent'
  return PALETTE[key] ?? MISSING_COLOUR
}
