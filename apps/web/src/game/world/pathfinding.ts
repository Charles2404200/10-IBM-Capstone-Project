/**
 * Click-to-move pathfinding.
 *
 * Holding a key to cross the floor is the wrong input for a workspace people
 * open twenty times a session: it makes the walk feel like a toll. Clicking a
 * destination and watching the avatar take itself there keeps the spatial model
 * — you still see the route and the rooms you pass — without charging for it.
 *
 * Breadth-first search is more than enough: the floor is under a thousand
 * walkable tiles, so a path is found in well under a millisecond, and BFS gives
 * the shortest route in tiles without the tuning surface of a heuristic.
 */
import { isWalkable, MAP_HEIGHT, MAP_WIDTH } from './map'

export interface TilePoint {
  tileX: number
  tileY: number
}

const NEIGHBOURS: ReadonlyArray<[number, number]> = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
]

function key(x: number, y: number): string {
  return `${x},${y}`
}

/**
 * Shortest walkable path from start to goal, excluding the start tile.
 * Returns an empty array when the goal is unreachable or is the start.
 */
export function findPath(start: TilePoint, goal: TilePoint): TilePoint[] {
  if (!isWalkable(goal.tileX, goal.tileY) || !isWalkable(start.tileX, start.tileY)) return []
  if (start.tileX === goal.tileX && start.tileY === goal.tileY) return []

  const goalKey = key(goal.tileX, goal.tileY)
  const cameFrom = new Map<string, string | null>()
  cameFrom.set(key(start.tileX, start.tileY), null)

  // A plain array with a head index beats shift() — shift is O(n) and this
  // queue can hold most of the floor.
  const queue: TilePoint[] = [start]
  let head = 0

  while (head < queue.length) {
    const current = queue[head]
    head += 1
    const currentKey = key(current.tileX, current.tileY)
    if (currentKey === goalKey) break

    for (const [dx, dy] of NEIGHBOURS) {
      const nx = current.tileX + dx
      const ny = current.tileY + dy
      if (nx < 0 || ny < 0 || nx >= MAP_WIDTH || ny >= MAP_HEIGHT) continue
      const nkey = key(nx, ny)
      if (cameFrom.has(nkey) || !isWalkable(nx, ny)) continue
      cameFrom.set(nkey, currentKey)
      queue.push({ tileX: nx, tileY: ny })
    }
  }

  if (!cameFrom.has(goalKey)) return []

  const path: TilePoint[] = []
  let cursor: string | null = goalKey
  while (cursor) {
    const [cx, cy] = cursor.split(',').map(Number)
    path.push({ tileX: cx, tileY: cy })
    cursor = cameFrom.get(cursor) ?? null
  }
  path.reverse()
  // Drop the start tile — the walker is already standing on it.
  return path.slice(1)
}

/**
 * Finds the nearest walkable tile to a click that landed on furniture or a
 * wall, so a slightly-off click still does something sensible instead of
 * nothing. Searches outward in rings; returns null if nothing is close.
 */
export function nearestWalkable(point: TilePoint, maxRadius = 4): TilePoint | null {
  if (isWalkable(point.tileX, point.tileY)) return point
  for (let radius = 1; radius <= maxRadius; radius += 1) {
    for (let dy = -radius; dy <= radius; dy += 1) {
      for (let dx = -radius; dx <= radius; dx += 1) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) !== radius) continue
        const nx = point.tileX + dx
        const ny = point.tileY + dy
        if (isWalkable(nx, ny)) return { tileX: nx, tileY: ny }
      }
    }
  }
  return null
}
