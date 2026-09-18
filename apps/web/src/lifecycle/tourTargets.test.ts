import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

const PAGES_DIR = join(process.cwd(), 'src/pages')

const TOUR_IDS = [
  'command-centre',
  'live-meeting',
  'meeting-preparation',
  'outreach-workspace',
  'proposal-studio',
  'client-intelligence',
  'drop-and-drop',
]

function getPageFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)

    if (entry.isDirectory()) {
      return getPageFiles(path)
    }

    return entry.name.endsWith('.tsx') ? [path] : []
  })
}

describe('walkthrough targets', () => {
  it('uses registered objective target classes', () => {
    const files = getPageFiles(PAGES_DIR)

    files.forEach((path) => {
      const source = readFileSync(path, 'utf8')

      const targets = [...source.matchAll(/objective-[a-z0-9-]+/g)].map((match) => match[0])

      targets.forEach((target) => {
        expect(target, `${path} uses an invalid walkthrough target`).toMatch(/^objective-[a-z0-9-]+$/)
      })
    })
  })

  it('mounts a registered tour id on every page that declares steps', () => {
    const files = getPageFiles(PAGES_DIR)

    files.forEach((path) => {
      const source = readFileSync(path, 'utf8')

      if (!source.includes('objectives:')) {
        return
      }

      const mounted = [...source.matchAll(/tourId:\s*['"]([^'"]+)['"]/g)].map((match) => match[1])

      expect(mounted.length, `${path} declares steps but mounts no tour`).toBeGreaterThan(0)

      mounted.forEach((id) => {
        expect(TOUR_IDS, `${path} mounts an unregistered tour id`).toContain(id)
      })
    })
  })

  /**
   * Two elements sharing a target class is not a compile error and not a
   * runtime one either: the tour silently anchors to whichever the browser
   * finds first, so reordering the markup moves the step somewhere else.
   *
   * The exception is a class deliberately placed on variants that never render
   * together. Those must be listed here, so the next duplicate has to be a
   * decision rather than an accident.
   */
  const MUTUALLY_EXCLUSIVE = new Set([
    // Client context before the first outreach, and the client's reply after
    // it; the learner is on one side of that line or the other.
    'objective-client',
  ])

  const files = getPageFiles(PAGES_DIR)
  const pages = files.map((path) => ({
    path,
    source: readFileSync(path, 'utf8'),
  }))

  const declared = pages.flatMap(({ path, source }) => {
    const matches = [...source.matchAll(/targets:\s*\[([\s\S]*?)\]/g)]

    return matches.flatMap((match) => {
      const targets = [...match[1].matchAll(/['"](\.objective-[a-z0-9-]+)['"]/g)]

      return targets.map((target) => ({
        path,
        className: target[1].slice(1),
      }))
    })
  })

  it.each(declared.filter((target) => !MUTUALLY_EXCLUSIVE.has(target.className)))(
    '$className anchors exactly one element',
    ({ path, className }) => {
      const source = pages.find((page) => page.path === path)!.source
      // The class as written on an element, never the '.selector' in the step.
      const onElements = source.match(new RegExp(`(?<![.\\w-])${className}(?![\\w-])`, 'g')) ?? []

      expect(
        onElements.length,
        `${className} is on ${onElements.length} elements in ${path}; a step can only anchor to one`,
      ).toBe(1)
    },
  )

  it('keeps every mutually exclusive target still present in the page', () => {
    MUTUALLY_EXCLUSIVE.forEach((className) => {
      const declaredHere = declared.filter((target) => target.className === className)
      expect(declaredHere.length, `${className} is listed as an exception but no step uses it`).toBeGreaterThan(0)

      declaredHere.forEach(({ path }) => {
        const source = pages.find((page) => page.path === path)!.source
        const onElements = source.match(new RegExp(`(?<![.\\w-])${className}(?![\\w-])`, 'g')) ?? []
        expect(onElements.length, `${className} no longer has variants in ${path}`).toBeGreaterThan(1)
      })
    })
  })
})