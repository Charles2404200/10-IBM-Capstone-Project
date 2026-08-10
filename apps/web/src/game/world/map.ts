/**
 * The hub: one floor of an IBM consulting office, authored as a tilemap.
 *
 * The floor plan is not decoration — it *is* the phase model. Each engagement
 * phase owns a physical station, and the walk between them is what gives the
 * learner a spatial memory of the engagement lifecycle. "Where am I in this
 * engagement?" stops being a question about a progress bar and becomes a
 * question about which room you are standing in.
 *
 * Terrain characters are lower-case/symbols; station characters are upper-case.
 * `assertMapIntegrity` (exercised by the world tests) guarantees the grid is
 * rectangular and that every station referenced by `STATIONS` actually exists.
 */
import type { EngagementPhase } from '@/api/types'

export const MAP_WIDTH = 38
export const MAP_HEIGHT = 14

/**
 * Wide and short, deliberately.
 *
 * The previous plan was 30x21 — very nearly square — while the space a browser
 * leaves below its chrome, the app header and the HUD is wide and shallow. The
 * map was therefore always bound by height and had to shrink to 1x to fit,
 * which is why it read as small and lost in empty space no matter how the
 * container was sized. No CSS fixes a shape mismatch; the plan has to match the
 * screen. At 38x14 it renders at 2x on a normal laptop and 1.5x on a short
 * window, instead of 1x on both.
 *
 * Layout: two room bands either side of a single corridor. Five rooms above,
 * six below, in lifecycle order left to right.
 */
/* eslint-disable no-multi-spaces */
export const HUB_MAP: readonly string[] = [
  '##w###w###w###w###w###w###w###w###w###',
  '#t.....#t.....#t.....#t.....#t...o...#',
  '#..C...#..L...#..I...#..O...#...P....#',
  '#......#......#......#......#........#',
  '#....dh#....dh#..eedh#....dh#......dh#',
  '##+######+######+######+######+#######',
  '#,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,#',
  '#,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,#',
  '##+#####+#####+#####+#####+#####+#####',
  '#f....#.....#.....#.....#.....#.....f#',
  '#..M..#..R..#..S..#..U..#..A..#..F...#',
  '#.....#.....#.....#.....#.....#......#',
  '#.yyy.#...dh#...dh#.y...#.qq..#.eee..#',
  '######################################',
]
/* eslint-enable no-multi-spaces */

// ─── Terrain semantics ───────────────────────────────────────────────────────

/** Characters the player may stand on. Everything else blocks movement. */
const WALKABLE = new Set(['.', ',', '+', 'C', 'L', 'I', 'O', 'P', 'M', 'R', 'S', 'U', 'A', 'F'])

export function isWalkable(tileX: number, tileY: number): boolean {
  if (tileX < 0 || tileY < 0 || tileX >= MAP_WIDTH || tileY >= MAP_HEIGHT) return false
  return WALKABLE.has(HUB_MAP[tileY][tileX])
}

export function tileAt(tileX: number, tileY: number): string {
  if (tileX < 0 || tileY < 0 || tileX >= MAP_WIDTH || tileY >= MAP_HEIGHT) return '#'
  return HUB_MAP[tileY][tileX]
}

// ─── Stations ────────────────────────────────────────────────────────────────

export interface Station {
  /** The tilemap character marking this station's floor pad. */
  glyph: string
  /** Engagement phase this station represents; `null` for always-open rooms. */
  phase: EngagementPhase | null
  /**
   * The room's name. For a station bound to a phase this MUST equal
   * `PHASE_LABEL[phase]` — one phase, one name, everywhere. A test enforces it.
   */
  title: string
  /** One plain-language sentence. No consulting jargon — this is the onboarding. */
  blurb: string
  /** Route to enter, relative to the engagement base, or an absolute path. */
  route: (engagementId: string | null) => string
  /** Accent used for the floor pad and label. */
  accent: 'blue' | 'teal' | 'purple' | 'green' | 'grey'
}

export const STATIONS: readonly Station[] = [
  {
    glyph: 'C',
    phase: null,
    title: 'Command Centre',
    blurb: 'Your desk. See every engagement you have running and pick up where you left off.',
    route: () => '/dashboard',
    accent: 'blue',
  },
  {
    glyph: 'L',
    phase: 'LEAD',
    title: 'Choose a client',
    blurb: 'Choose which client to chase. You only get a little information up front — that is the point.',
    route: (id) => `/dashboard/engagements/${id}/leads`,
    accent: 'blue',
  },
  {
    glyph: 'I',
    phase: 'CLIENT_INTELLIGENCE',
    title: 'Research the client',
    blurb: 'Dig up facts about the client and commit to a hypothesis before you contact anyone.',
    route: (id) => `/dashboard/engagements/${id}/intelligence`,
    accent: 'teal',
  },
  {
    glyph: 'O',
    phase: 'OUTREACH',
    title: 'Make contact',
    blurb: 'Write the first email. The client owes you nothing and may simply ignore it.',
    route: (id) => `/dashboard/engagements/${id}/outreach`,
    accent: 'blue',
  },
  {
    glyph: 'P',
    phase: 'MEETING_PREPARATION',
    title: 'Prepare',
    blurb: 'Decide what you want out of the meeting and what you will ask, before you walk in.',
    route: (id) => `/dashboard/engagements/${id}/preparation`,
    accent: 'teal',
  },
  {
    glyph: 'M',
    phase: 'LIVE_MEETING',
    title: 'The meeting',
    blurb: 'Sit down with the client. They react to what you actually say — there is no script.',
    route: (id) => `/dashboard/engagements/${id}/preparation`,
    accent: 'purple',
  },
  {
    glyph: 'R',
    phase: 'MEETING_REVIEW',
    title: 'Debrief',
    blurb: 'Go back over the meeting: what you learned, what you missed, what it cost you.',
    route: (id) => `/dashboard/engagements/${id}/assessment`,
    accent: 'grey',
  },
  {
    glyph: 'S',
    phase: 'PROPOSAL',
    title: 'Proposal',
    blurb: 'Turn evidence into a recommendation with a budget, a timeline and named risks.',
    route: (id) => `/dashboard/engagements/${id}/proposal`,
    accent: 'blue',
  },
  {
    glyph: 'U',
    phase: 'OUTCOME',
    title: 'Their decision',
    blurb: 'The client decides. Your research, your relationship and your proposal all count.',
    route: (id) => `/dashboard/engagements/${id}/assessment`,
    accent: 'green',
  },
  {
    glyph: 'A',
    phase: 'REVIEW',
    title: 'Your review',
    blurb: 'A structured read on how you performed, and which competencies to work on next.',
    route: (id) => `/dashboard/engagements/${id}/assessment`,
    accent: 'purple',
  },
  {
    glyph: 'F',
    phase: 'COMPLETED',
    title: 'Portfolio',
    blurb: 'Your record: engagements run, contracts won, and how your competencies are trending.',
    route: () => '/dashboard/portfolio',
    accent: 'green',
  },
]

export const STATION_BY_GLYPH: ReadonlyMap<string, Station> = new Map(
  STATIONS.map((s) => [s.glyph, s])
)

export interface StationPlacement extends Station {
  tileX: number
  tileY: number
}

/** Scans the map once and resolves every station glyph to its tile coordinate. */
export function placeStations(): StationPlacement[] {
  const found: StationPlacement[] = []
  for (let y = 0; y < MAP_HEIGHT; y += 1) {
    for (let x = 0; x < MAP_WIDTH; x += 1) {
      const station = STATION_BY_GLYPH.get(HUB_MAP[y][x])
      if (station) found.push({ ...station, tileX: x, tileY: y })
    }
  }
  return found
}

/** Where the player character spawns — the main corridor, in reach of everything. */
export const SPAWN = { tileX: 19, tileY: 6 } as const

/** Where Sarah Chen stands in the meeting room, beside the table. */
export const SARAH_POSITION = { tileX: 4, tileY: 10 } as const

// ─── Integrity ───────────────────────────────────────────────────────────────

export class MapError extends Error {}

/**
 * Fails loudly on a malformed map. Called by the world tests so a mistyped row
 * is a red test rather than an invisible hole in the floor.
 */
export function assertMapIntegrity(): void {
  if (HUB_MAP.length !== MAP_HEIGHT) {
    throw new MapError(`Map has ${HUB_MAP.length} rows, expected ${MAP_HEIGHT}`)
  }
  HUB_MAP.forEach((row, y) => {
    if (row.length !== MAP_WIDTH) {
      throw new MapError(`Map row ${y} has width ${row.length}, expected ${MAP_WIDTH}`)
    }
  })

  const placed = placeStations()
  for (const station of STATIONS) {
    if (!placed.some((p) => p.glyph === station.glyph)) {
      throw new MapError(`Station "${station.title}" (glyph ${station.glyph}) is not on the map`)
    }
  }

  if (!isWalkable(SPAWN.tileX, SPAWN.tileY)) {
    throw new MapError('Spawn point is not walkable')
  }

  // Every station must be reachable from spawn, or the learner can get stranded.
  const reachable = floodFill(SPAWN.tileX, SPAWN.tileY)
  for (const station of placed) {
    if (!reachable.has(`${station.tileX},${station.tileY}`)) {
      throw new MapError(`Station "${station.title}" is unreachable from spawn`)
    }
  }
}

/** Four-way flood fill over walkable tiles; used by the reachability check. */
export function floodFill(startX: number, startY: number): Set<string> {
  const seen = new Set<string>()
  const queue: Array<[number, number]> = [[startX, startY]]
  while (queue.length > 0) {
    const [x, y] = queue.pop() as [number, number]
    const key = `${x},${y}`
    if (seen.has(key) || !isWalkable(x, y)) continue
    seen.add(key)
    queue.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1])
  }
  return seen
}
