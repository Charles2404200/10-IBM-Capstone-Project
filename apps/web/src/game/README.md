# The game layer

A presentation layer that sits over the existing phase workspaces. It adds a walkable
pixel-art hub, a persistent HUD, an onboarding sequence, and per-phase framing.

It does **not** own simulation truth. Phase, trust/interest/patience, scores and unlocks all
come from the Spring Boot API through the existing `@/api/hooks/*` react-query hooks. Nothing
here duplicates or second-guesses server state.

## Why it exists

The API already computes almost everything a game needs, but the UI surfaced it unevenly: the
phase stepper rendered on some workspaces and not others, relationship state was visible only
inside the live meeting, and the outreach rubric was only shown *after* a rejection. This layer
surfaces what was already there, in the places a learner needs it to make a decision.

The full rationale, including the Technology Acceptance Model analysis behind each change and
the measurement plan, is in the PR description.

## Layout

```
art/         Sprites as palette-indexed string art; procedural floor/wall surfaces.
audio/       Web Audio synthesis. No audio files.
world/       Tilemap, walk/collision engine, canvas renderer.
state/       Client-side preferences and phase → lock-state mapping.
coaching/    Outreach self-check heuristics (pure functions).
components/  React: HubWorld, GameHUD, DayOne, PhaseBrief, LiveRubric, WorldPage.
styles/      One shared CSS module.
```

Dependency direction is one-way: `components/` → `world/` → `art/`. Nothing in `art/`,
`world/`, `state/` or `coaching/` imports React, so all of it is unit-testable without a DOM
renderer.

## Integration points

Only three existing files are touched:

| File | Change |
|---|---|
| `components/layout/AppShell.tsx` | Renders `<GameHUD />` above `<Content>`; adds an "Office floor" nav item. |
| `App.tsx` | Adds the `/dashboard/world` route, plus a dev-only `/world-preview`. |
| `pages/OutreachWorkspace/OutreachWorkspacePage.tsx` | Adds `<PhaseBrief />` and `<LiveRubric />`, and a live character counter. |

To remove the layer entirely: revert those three files and delete `src/game`.

## Art

Sprites are arrays of equal-length strings; each character indexes `art/palette.ts`. They are
rasterised once into offscreen canvases and then blitted.

Three poses per character are authored by hand — front, back and right. "Left" is mirrored from
right, and walk frames are synthesised by bobbing the leg rows, so a four-frame cycle in four
directions costs three drawings.

Adding a sprite: add it to the module, and add it to the module's `ALL_*` export so
`game.test.ts` asserts it is rectangular and uses only declared palette keys. A miscounted row
should fail CI, not render torn.

The whole floor plan composites once into a 480×336 canvas (`world/renderer.ts`), so a frame
costs one `drawImage` plus a few actor blits. `fitZoom` then picks the largest integer zoom that
fits the entire plan on screen — seeing all eleven rooms at once is what stops the world reading
as a corridor.

## Map

`world/map.ts` holds the tilemap as strings (30x21). Lower-case characters and symbols are
terrain; upper-case characters are station pads bound to an `EngagementPhase`.

`assertMapIntegrity()` checks the grid is rectangular, that every declared station is placed,
and — via a flood fill from the spawn point — that every station is reachable on foot. Run it
after any map edit; `game.test.ts` does this automatically.

**Rooms, not pads.** `world/rooms.ts` flood-fills the floor into rooms, treating walls *and*
doorways as boundaries, so pressing E anywhere inside a room enters it. Keep every station room
at or under `LARGE_ROOM_TILES` (90) — above that the room is treated as a hall and interaction
falls back to pad proximity, which is what the original oversized floor plan did and what made
it feel fiddly. A test asserts this for every station room.

**Editing the map.** Doorways must have walkable floor on both sides — a prop placed in front
of a door silently seals the room. The reachability test catches it.

## Audio

Everything is synthesised (`audio/engine.ts`). The `AudioContext` is only created on a user
gesture, per browser policy. Volume defaults are deliberately low, and no cue carries
information that is not also visible on screen.

`audio.setTension(calm)` raises the music tempo as the client's patience drops.

## Previewing without a backend

```
npm run dev   →   http://localhost:3000/world-preview
```

Dev builds only. Lets you switch phase, client mood and starting room to inspect the art and the
lock states with no API, login or seeded database.

## Accessibility rules this layer must keep

- The world is optional (`preferences.worldEnabled`, persisted) and is never the only route to a
  station — `WorldPage` renders every room as a real button.
- Movement has three inputs: arrow keys, WASD, and click-to-move (`world/pathfinding.ts`, BFS).
  Keyboard input always cancels a clicked route rather than fighting it.
- The canvas is `aria-hidden`; the surrounding DOM carries the accessible description.
- `prefers-reduced-motion` holds the walk cycle on its rest pose and disables transitions.
- Audio is mutable from the HUD and defaults quiet.
- The first frame paints synchronously, so a throttled or background tab never shows an empty
  black rectangle.

## Tests

`game.test.ts` and `coaching/outreachRubric.test.ts` — 101 tests covering sprite integrity, map
geometry and reachability, room detection, movement and wall-sliding, keyboard mapping,
pathfinding, phase gating, and the rubric heuristics.

```
npx vitest run src/game
```
