/**
 * Character sprites, authored as string art.
 *
 * Every body is 16x24 with the figure occupying the middle 10 columns, which
 * keeps rows a fixed width and makes miscounts impossible to miss (the art test
 * asserts rectangularity for every export in this module).
 *
 * Only three poses are authored per character — front, back and right-facing.
 * "Left" is derived by mirroring, and walk frames are derived by bobbing the leg
 * rows, so a full eight-frame cycle costs three hand-drawn poses.
 */
import type { Sprite } from './pixels'
import { bobLegs, recolour } from './pixels'

/** Row range covering the legs, used to synthesise the walk cycle. */
export const LEG_ROWS: readonly [number, number] = [17, 24]

// ─── Player: a new IBM consultant in a navy suit ─────────────────────────────

export const PLAYER_DOWN: Sprite = [
  '................',
  '................',
  '.....kkkkkk.....',
  '....khhhhhhk....',
  '...khhhhhhhhk...',
  '...khhhhhhhhk...',
  '...khssssssSk...',
  '...khskssksSk...',
  '...khsssssssk...',
  '....kssssssk....',
  '.....kwwwwk.....',
  '....kbwttwbk....',
  '...kbbwttwbbk...',
  '...kbbwttwbbk...',
  '...kbbwttwbbk...',
  '...kbbbttbbbk...',
  '...kbbbbbbbbk...',
  '....kKKKKKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kkkk.kkk....',
]

export const PLAYER_UP: Sprite = [
  '................',
  '................',
  '.....kkkkkk.....',
  '....khhhhhhk....',
  '...khhhhhhhhk...',
  '...khhhhhhhhk...',
  '...khhhhhhhhk...',
  '...khhhhhhhhk...',
  '...khhhhhhhhk...',
  '....khhhhhhk....',
  '.....kwwwwk.....',
  '....kbbbbbbk....',
  '...kbbbbbbbbk...',
  '...kbbbbbbbbk...',
  '...kbbbbbbbbk...',
  '...kbbbbbbbbk...',
  '...kbbbbbbbbk...',
  '....kKKKKKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kkkk.kkk....',
]

export const PLAYER_RIGHT: Sprite = [
  '................',
  '................',
  '.....kkkkkk.....',
  '....khhhhhhhk...',
  '...khhhhhhhhk...',
  '...khhhhhhhhk...',
  '...khhssssssk...',
  '...khhskssssk...',
  '...khhsssssSk...',
  '....khssssssk...',
  '.....kwwwwwk....',
  '....kbbwttwbk...',
  '....kbbbwttbk...',
  '....kbbbwttbk...',
  '....kbbbbwtbk...',
  '....kbbbbbbbk...',
  '....kbbbbbbbk...',
  '.....kKKKKKk....',
  '.....kKKKKKk....',
  '.....kKKkKKk....',
  '.....kKKkKKk....',
  '.....kKKkKKk....',
  '.....kKKkKKk....',
  '.....kkkkkkk....',
]

// ─── Sarah Chen: CIO, MediCare Regional Hospital Network ─────────────────────
// The scenario's primary persona. Four moods share one body; only the face rows
// differ, so her silhouette stays recognisable while her state reads instantly.

const SARAH_BODY_TAIL: Sprite = [
  '....kssssssk....',
  '.....kLLLLk.....',
  '....kmLqqLmk....',
  '...kmmLqqLmmk...',
  '...kmmLqqLmmk...',
  '...kmmLqqLmmk...',
  '...kmmmqqmmmk...',
  '...kmmmmmmmmk...',
  '....kKKKKKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kKKkkKKk....',
  '....kkkk.kkk....',
]

const SARAH_HEAD_TOP: Sprite = [
  '................',
  '.....kkkkkk.....',
  '....khhhhhhk....',
  '...khhhhhhhhk...',
  '..khhhhhhhhhhk..',
  '..khhssssssHhk..',
]

/** Face rows 7-8 vary by mood; everything else is shared. */
const SARAH_FACES: Record<string, Sprite> = {
  neutral: ['..khskssksshk...', '..khsssssssshk..'],
  engaged: ['..khsVssVsschk..', '..khssvvvvsshk..'],
  sceptical: ['..khskkssksshk..', '..khsssXXsssshk.'],
  impatient: ['..khsyssyssshk..', '..khssXXXXsshk..'],
}

export type SarahMood = keyof typeof SARAH_FACES

function buildSarah(mood: SarahMood): Sprite {
  return [...SARAH_HEAD_TOP, ...SARAH_FACES[mood], ...SARAH_BODY_TAIL]
}

export const SARAH_MOODS: Record<SarahMood, Sprite> = {
  neutral: buildSarah('neutral'),
  engaged: buildSarah('engaged'),
  sceptical: buildSarah('sceptical'),
  impatient: buildSarah('impatient'),
}

// ─── Ambient colleagues ──────────────────────────────────────────────────────
// Derived from the player body by palette substitution: same silhouette, clearly
// different people. Cheap variety that still looks hand-made.

export const COLLEAGUE_ANALYST: Sprite = recolour(PLAYER_DOWN, {
  h: 'z', // lighter hair
  b: 'm', // teal jacket
  s: 'S',
  S: 't',
})

export const COLLEAGUE_PARTNER: Sprite = recolour(PLAYER_DOWN, {
  h: 'N', // grey hair
  b: 'd', // charcoal suit
  t: 'y', // red tie
})

export const COLLEAGUE_MENTOR: Sprite = recolour(PLAYER_DOWN, {
  h: 'H',
  b: 'p', // aubergine
  t: 'O',
  s: 't',
  S: 'T',
})

// ─── Walk cycle synthesis ────────────────────────────────────────────────────

export type Facing = 'down' | 'up' | 'left' | 'right'

export interface ActorPose {
  sprite: Sprite
  flipX: boolean
}

const BASE_BY_FACING: Record<Facing, ActorPose> = {
  down: { sprite: PLAYER_DOWN, flipX: false },
  up: { sprite: PLAYER_UP, flipX: false },
  right: { sprite: PLAYER_RIGHT, flipX: false },
  left: { sprite: PLAYER_RIGHT, flipX: true },
}

/**
 * Returns the pose for a facing/frame pair. Frame 0 and 2 are the neutral
 * stance; frames 1 and 3 raise alternate legs, giving a four-beat cycle.
 */
export function playerPose(facing: Facing, frame: number): ActorPose {
  const base = BASE_BY_FACING[facing]
  const step = frame % 4
  if (step === 0 || step === 2) return base
  const direction = step === 1 ? 1 : -1
  return {
    sprite: bobLegs(base.sprite, LEG_ROWS[0], LEG_ROWS[1], direction),
    flipX: base.flipX,
  }
}

/** Every sprite in this module, for the rectangularity test. */
export const ALL_ACTOR_SPRITES: Record<string, Sprite> = {
  PLAYER_DOWN,
  PLAYER_UP,
  PLAYER_RIGHT,
  COLLEAGUE_ANALYST,
  COLLEAGUE_PARTNER,
  COLLEAGUE_MENTOR,
  SARAH_neutral: SARAH_MOODS.neutral,
  SARAH_engaged: SARAH_MOODS.engaged,
  SARAH_sceptical: SARAH_MOODS.sceptical,
  SARAH_impatient: SARAH_MOODS.impatient,
}
