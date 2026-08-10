import { describe, expect, it } from 'vitest'
import { ALL_ACTOR_SPRITES, playerPose } from './art/actors'
import { PALETTE } from './art/palette'
import { assertRectangular, bobLegs, recolour, spriteSize } from './art/pixels'
import { PROPS } from './art/tiles'
import {
  assertMapIntegrity,
  floodFill,
  HUB_MAP,
  isWalkable,
  MAP_HEIGHT,
  MAP_WIDTH,
  placeStations,
  SPAWN,
  STATIONS,
} from './world/map'
import {
  axisFromKeys,
  canStand,
  initialPlayer,
  nearestStation,
  step,
  WALK_SPEED,
} from './world/engine'
import {
  lifecycleProgress,
  PHASE_BRIEF,
  PHASE_LABEL,
  PHASE_ORDER,
  phaseIndex,
  stationStatus,
} from './state/progression'
import { engagementIdFromPath } from './components/GameHUD'
import { moodFor, selectActiveEngagement } from './components/WorldPage'
import type { Engagement, EngagementPhase } from '@/api/types'

// ─── Art ─────────────────────────────────────────────────────────────────────

describe('sprite art', () => {
  it('every actor sprite is rectangular', () => {
    for (const [name, sprite] of Object.entries(ALL_ACTOR_SPRITES)) {
      expect(() => assertRectangular(name, sprite)).not.toThrow()
    }
  })

  it('every prop is a rectangular 16x16 tile', () => {
    for (const [name, sprite] of Object.entries(PROPS)) {
      expect(() => assertRectangular(name, sprite)).not.toThrow()
      expect(spriteSize(sprite)).toEqual({ width: 16, height: 16 })
    }
  })

  it('uses only declared palette keys', () => {
    const known = new Set(Object.keys(PALETTE))
    const everything = { ...ALL_ACTOR_SPRITES, ...PROPS }
    for (const [name, sprite] of Object.entries(everything)) {
      for (const row of sprite) {
        for (const ch of row) {
          expect(known.has(ch), `${name} uses undeclared palette key "${ch}"`).toBe(true)
        }
      }
    }
  })

  it('rejects a ragged sprite', () => {
    expect(() => assertRectangular('ragged', ['....', '...'])).toThrow(/width/)
  })

  it('recolour swaps keys without changing dimensions', () => {
    const original = ALL_ACTOR_SPRITES.PLAYER_DOWN
    const swapped = recolour(original, { b: 'm' })
    expect(spriteSize(swapped)).toEqual(spriteSize(original))
    expect(swapped.join('')).not.toContain('b')
    expect(swapped.join('')).toContain('m')
  })

  it('bobLegs preserves sprite dimensions', () => {
    const original = ALL_ACTOR_SPRITES.PLAYER_DOWN
    for (const direction of [1, -1] as const) {
      const bobbed = bobLegs(original, 17, 24, direction)
      expect(spriteSize(bobbed)).toEqual(spriteSize(original))
    }
  })

  it('produces a four-beat walk cycle where frames 0 and 2 are the rest pose', () => {
    const rest = playerPose('down', 0)
    expect(playerPose('down', 2).sprite).toEqual(rest.sprite)
    expect(playerPose('down', 1).sprite).not.toEqual(rest.sprite)
    expect(playerPose('down', 3).sprite).not.toEqual(rest.sprite)
    expect(playerPose('down', 1).sprite).not.toEqual(playerPose('down', 3).sprite)
  })

  it('derives left by mirroring right rather than authoring a fourth pose', () => {
    expect(playerPose('left', 0).flipX).toBe(true)
    expect(playerPose('right', 0).flipX).toBe(false)
    expect(playerPose('left', 0).sprite).toEqual(playerPose('right', 0).sprite)
  })
})

// ─── Map ─────────────────────────────────────────────────────────────────────

describe('hub map', () => {
  it('passes its own integrity check', () => {
    expect(() => assertMapIntegrity()).not.toThrow()
  })

  it('is exactly the declared size', () => {
    expect(HUB_MAP).toHaveLength(MAP_HEIGHT)
    for (const row of HUB_MAP) expect(row).toHaveLength(MAP_WIDTH)
  })

  it('is fully enclosed by walls', () => {
    for (let x = 0; x < MAP_WIDTH; x += 1) {
      expect(isWalkable(x, 0)).toBe(false)
      expect(isWalkable(x, MAP_HEIGHT - 1)).toBe(false)
    }
    for (let y = 0; y < MAP_HEIGHT; y += 1) {
      expect(isWalkable(0, y)).toBe(false)
      expect(isWalkable(MAP_WIDTH - 1, y)).toBe(false)
    }
  })

  it('places every declared station exactly once', () => {
    const placed = placeStations()
    expect(placed).toHaveLength(STATIONS.length)
    const glyphs = placed.map((p) => p.glyph)
    expect(new Set(glyphs).size).toBe(glyphs.length)
  })

  it('leaves every station reachable on foot from the spawn point', () => {
    const reachable = floodFill(SPAWN.tileX, SPAWN.tileY)
    for (const station of placeStations()) {
      expect(
        reachable.has(`${station.tileX},${station.tileY}`),
        `${station.title} is walled off`
      ).toBe(true)
    }
  })

  it('treats out-of-bounds tiles as solid', () => {
    expect(isWalkable(-1, 5)).toBe(false)
    expect(isWalkable(5, -1)).toBe(false)
    expect(isWalkable(MAP_WIDTH, 5)).toBe(false)
    expect(isWalkable(5, MAP_HEIGHT)).toBe(false)
  })
})

// ─── Movement ────────────────────────────────────────────────────────────────

describe('movement', () => {
  it('spawns the player somewhere they can stand', () => {
    const player = initialPlayer()
    expect(canStand(player.x, player.y)).toBe(true)
  })

  it('stays put with no input and resets the walk frame', () => {
    const player = { ...initialPlayer(), frame: 3, moving: true }
    const next = step(player, { x: 0, y: 0 }, 0.016)
    expect(next.x).toBe(player.x)
    expect(next.y).toBe(player.y)
    expect(next.moving).toBe(false)
    expect(next.frame).toBe(0)
  })

  it('moves at the declared speed on an open floor', () => {
    const player = initialPlayer()
    const next = step(player, { x: 1, y: 0 }, 0.1)
    expect(next.x - player.x).toBeCloseTo(WALK_SPEED * 0.1, 5)
  })

  it('normalises diagonal input so diagonals are not faster', () => {
    const player = initialPlayer()
    const straight = step(player, { x: 1, y: 0 }, 0.1)
    const diagonal = step(player, { x: 1, y: 1 }, 0.1)
    const straightDistance = Math.hypot(straight.x - player.x, straight.y - player.y)
    const diagonalDistance = Math.hypot(diagonal.x - player.x, diagonal.y - player.y)
    // Diagonal may be clipped by geometry, but must never exceed the straight case.
    expect(diagonalDistance).toBeLessThanOrEqual(straightDistance + 1e-6)
  })

  it('refuses to walk into a wall', () => {
    // Hard against the top-left interior corner, pushing up and left.
    const player = { ...initialPlayer(), x: 20, y: 40 }
    let current = player
    for (let i = 0; i < 200; i += 1) current = step(current, { x: -1, y: -1 }, 0.016)
    expect(canStand(current.x, current.y)).toBe(true)
  })

  it('slides along a wall instead of sticking when pushing into it diagonally', () => {
    // Stand in the lobby hard against its north wall, then push up-and-right:
    // the Y axis is blocked by the wall but X must still advance.
    const player = { ...initialPlayer(), x: 300, y: 38 }
    expect(canStand(player.x, player.y)).toBe(true)

    const blocked = step(player, { x: 0, y: -1 }, 0.2)
    expect(blocked.y).toBe(player.y)

    const sliding = step(player, { x: 1, y: -1 }, 0.2)
    expect(sliding.y).toBe(player.y)
    expect(sliding.x).toBeGreaterThan(player.x)
  })

  it('faces the direction of travel, preferring vertical on a tie', () => {
    const player = initialPlayer()
    expect(step(player, { x: 1, y: 0 }, 0.05).facing).toBe('right')
    expect(step(player, { x: -1, y: 0 }, 0.05).facing).toBe('left')
    expect(step(player, { x: 0, y: 1 }, 0.05).facing).toBe('down')
    expect(step(player, { x: 0, y: -1 }, 0.05).facing).toBe('up')
    // Perfect diagonal resolves to horizontal, since |y| is not greater than |x|.
    expect(step(player, { x: 1, y: 1 }, 0.05).facing).toBe('right')
  })

  it('keeps the previous facing when input stops', () => {
    const player = { ...initialPlayer(), facing: 'left' as const }
    expect(step(player, { x: 0, y: 0 }, 0.05).facing).toBe('left')
  })
})

describe('keyboard mapping', () => {
  it('reads both arrow keys and WASD', () => {
    expect(axisFromKeys(new Set(['ArrowRight']))).toEqual({ x: 1, y: 0 })
    expect(axisFromKeys(new Set(['d']))).toEqual({ x: 1, y: 0 })
    expect(axisFromKeys(new Set(['W']))).toEqual({ x: 0, y: -1 })
  })

  it('cancels opposing keys held together', () => {
    expect(axisFromKeys(new Set(['ArrowLeft', 'ArrowRight']))).toEqual({ x: 0, y: 0 })
  })

  it('clamps so three keys cannot produce a magnitude above one per axis', () => {
    const axis = axisFromKeys(new Set(['ArrowRight', 'd', 'ArrowDown']))
    expect(axis.x).toBe(1)
    expect(axis.y).toBe(1)
  })

  it('ignores unrelated keys', () => {
    expect(axisFromKeys(new Set(['q', 'Shift']))).toEqual({ x: 0, y: 0 })
  })
})

describe('station proximity', () => {
  it('finds nothing when standing away from every pad', () => {
    expect(nearestStation({ ...initialPlayer(), x: 8, y: 220 })).toBeNull()
  })

  it('detects the station the player is standing on', () => {
    const command = placeStations().find((s) => s.glyph === 'C')
    if (!command) throw new Error('Command Centre missing from the map')
    const near = nearestStation({
      ...initialPlayer(),
      x: command.tileX * 16 + 8,
      y: command.tileY * 16 + 8,
    })
    expect(near?.station.glyph).toBe('C')
  })
})

// ─── Progression ─────────────────────────────────────────────────────────────

function engagementAt(phase: EngagementPhase, overrides: Partial<Engagement> = {}): Engagement {
  return {
    id: 'e1',
    userId: 'u1',
    scenarioId: 's1',
    personaId: 'p1',
    state: 'OUTREACHING',
    selectedLeadId: 'l1',
    createdAt: '2026-08-01T00:00:00Z',
    completedAt: null,
    events: [],
    scenarioTitle: 'MediCare Digital Transformation',
    scenarioIndustry: 'Healthcare',
    leadCompanyName: 'MediCare Regional Hospital Network',
    phase,
    phaseLabel: phase,
    progressPercent: 0,
    nextAction: 'Respond to outreach consequences',
    evidenceCount: 6,
    daysElapsed: 0,
    meetingId: null,
    ...overrides,
  }
}

describe('progression', () => {
  it('covers every phase with a label and a brief', () => {
    for (const phase of PHASE_ORDER) {
      expect(PHASE_LABEL[phase]).toBeTruthy()
      expect(PHASE_BRIEF[phase].goal).toBeTruthy()
      expect(PHASE_BRIEF[phase].done).toBeTruthy()
      expect(PHASE_BRIEF[phase].next).toBeTruthy()
    }
  })

  it('orders phases without gaps or duplicates', () => {
    expect(new Set(PHASE_ORDER).size).toBe(PHASE_ORDER.length)
    PHASE_ORDER.forEach((phase, index) => expect(phaseIndex(phase)).toBe(index))
  })

  it('marks earlier phases done, the current one current and later ones locked', () => {
    const engagement = engagementAt('OUTREACH')
    expect(stationStatus('LEAD', engagement)).toBe('done')
    expect(stationStatus('CLIENT_INTELLIGENCE', engagement)).toBe('done')
    expect(stationStatus('OUTREACH', engagement)).toBe('current')
    expect(stationStatus('LIVE_MEETING', engagement)).toBe('locked')
    expect(stationStatus('COMPLETED', engagement)).toBe('locked')
  })

  it('treats phaseless rooms as always open', () => {
    expect(stationStatus(null, null)).toBe('open')
    expect(stationStatus(null, engagementAt('LEAD'))).toBe('open')
  })

  it('offers only the lead station when nothing is running', () => {
    expect(stationStatus('LEAD', null)).toBe('current')
    expect(stationStatus('OUTREACH', null)).toBe('locked')
  })

  it('reports lifecycle progress between 0 and 1', () => {
    expect(lifecycleProgress(null)).toBe(0)
    expect(lifecycleProgress(engagementAt('LEAD'))).toBe(0)
    expect(lifecycleProgress(engagementAt('COMPLETED'))).toBe(1)
    expect(lifecycleProgress(engagementAt('OUTREACH'))).toBeCloseTo(2 / 9, 5)
  })
})

// ─── Page helpers ────────────────────────────────────────────────────────────

describe('engagementIdFromPath', () => {
  it('extracts the id from every workspace route', () => {
    expect(engagementIdFromPath('/dashboard/engagements/abc-123/outreach')).toBe('abc-123')
    expect(engagementIdFromPath('/dashboard/engagements/abc-123/meetings/m-9')).toBe('abc-123')
    expect(engagementIdFromPath('/dashboard/engagements/abc-123')).toBe('abc-123')
  })

  it('returns null away from an engagement', () => {
    expect(engagementIdFromPath('/dashboard')).toBeNull()
    expect(engagementIdFromPath('/dashboard/portfolio')).toBeNull()
    expect(engagementIdFromPath('/dashboard/world')).toBeNull()
  })
})

describe('selectActiveEngagement', () => {
  it('returns null with no engagements', () => {
    expect(selectActiveEngagement(undefined)).toBeNull()
    expect(selectActiveEngagement([])).toBeNull()
  })

  it('prefers an in-flight engagement over a completed one', () => {
    const done = engagementAt('COMPLETED', { id: 'done', createdAt: '2026-08-09T00:00:00Z' })
    const live = engagementAt('OUTREACH', { id: 'live', createdAt: '2026-08-01T00:00:00Z' })
    expect(selectActiveEngagement([done, live])?.id).toBe('live')
  })

  it('falls back to the most recent completed engagement when nothing is live', () => {
    const older = engagementAt('COMPLETED', { id: 'older', createdAt: '2026-07-01T00:00:00Z' })
    const newer = engagementAt('COMPLETED', { id: 'newer', createdAt: '2026-08-01T00:00:00Z' })
    expect(selectActiveEngagement([older, newer])?.id).toBe('newer')
  })

  it('picks the newest when several are live', () => {
    const a = engagementAt('LEAD', { id: 'a', createdAt: '2026-07-01T00:00:00Z' })
    const b = engagementAt('PROPOSAL', { id: 'b', createdAt: '2026-08-05T00:00:00Z' })
    expect(selectActiveEngagement([a, b])?.id).toBe('b')
  })
})

describe('moodFor', () => {
  it('is neutral before the client has met you', () => {
    expect(moodFor(null, null, null)).toBe('neutral')
  })

  it('reads impatience first, because running out of time ends the meeting', () => {
    expect(moodFor(90, 90, 20)).toBe('impatient')
  })

  it('reads scepticism when trust is low but time remains', () => {
    expect(moodFor(40, 80, 80)).toBe('sceptical')
  })

  it('is engaged only when both trust and interest clear the threshold', () => {
    expect(moodFor(75, 75, 80)).toBe('engaged')
    expect(moodFor(75, 60, 80)).toBe('neutral')
  })
})
