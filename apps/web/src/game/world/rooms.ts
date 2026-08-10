/**
 * Room detection.
 *
 * Playtesting showed the original rule — stand on the exact floor pad to
 * interact — was fiddly enough to spoil the whole world: players walked into a
 * room, pressed E, nothing happened, and had to hunt for the tile. The pad is a
 * signpost, not a switch.
 *
 * So a room is now the interaction unit. Rooms are derived from the map rather
 * than declared separately: a flood fill that treats walls **and doorways** as
 * boundaries carves the floor into exactly the spaces a person would call
 * rooms. Being anywhere inside one is enough.
 */
import { HUB_MAP, MAP_HEIGHT, MAP_WIDTH, STATION_BY_GLYPH, isWalkable, type Station } from './map'

export interface Room {
  id: number
  tiles: Set<string>
  /** The station this room contains, if any. */
  station: Station | null
  /** Tile coordinate of the station pad, for the camera and overhead markers. */
  padX: number
  padY: number
}

/** Doorways separate rooms; standing in one belongs to neither side. */
function isBoundary(tileX: number, tileY: number): boolean {
  return HUB_MAP[tileY][tileX] === '+'
}

function buildRooms(): { rooms: Room[]; index: Map<string, number> } {
  const index = new Map<string, number>()
  const rooms: Room[] = []

  for (let y = 0; y < MAP_HEIGHT; y += 1) {
    for (let x = 0; x < MAP_WIDTH; x += 1) {
      const key = `${x},${y}`
      if (index.has(key) || !isWalkable(x, y) || isBoundary(x, y)) continue

      // New region — flood it.
      const id = rooms.length
      const tiles = new Set<string>()
      let station: Station | null = null
      let padX = x
      let padY = y

      const queue: Array<[number, number]> = [[x, y]]
      while (queue.length > 0) {
        const [cx, cy] = queue.pop() as [number, number]
        const ckey = `${cx},${cy}`
        if (tiles.has(ckey) || !isWalkable(cx, cy) || isBoundary(cx, cy)) continue
        tiles.add(ckey)
        index.set(ckey, id)

        const found = STATION_BY_GLYPH.get(HUB_MAP[cy][cx])
        if (found) {
          station = found
          padX = cx
          padY = cy
        }

        queue.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1])
      }

      rooms.push({ id, tiles, station, padX, padY })
    }
  }

  return { rooms, index }
}

const { rooms: ROOMS, index: TILE_TO_ROOM } = buildRooms()

export function allRooms(): readonly Room[] {
  return ROOMS
}

/** The room containing a tile, or null in a doorway, wall or void. */
export function roomAtTile(tileX: number, tileY: number): Room | null {
  const id = TILE_TO_ROOM.get(`${tileX},${tileY}`)
  return id === undefined ? null : ROOMS[id]
}

/** Rooms that contain a station — the ones a player can actually enter. */
export function stationRooms(): Room[] {
  return ROOMS.filter((room) => room.station !== null)
}
