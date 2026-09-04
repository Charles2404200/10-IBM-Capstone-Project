/**
 * Guards the thread between a walkthrough step and the element it points at.
 *
 * A step names its anchor with a CSS class written somewhere else in the same
 * page. Nothing connects the two except the string, so renaming the class — or
 * moving the section it sits on — leaves a step aiming at nothing, and the
 * failure is silent: the walkthrough simply drops that step and carries on.
 *
 * Reading the pages as text is the only way to catch that, since the class only
 * exists in markup that a unit test would have to render the whole page to see.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { TOUR_IDS } from './tours'

const PAGES_DIR = join(process.cwd(), 'src', 'pages')

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return path.endsWith('.tsx') ? [path] : []
  })
}

const pages = sourceFiles(PAGES_DIR)
  .filter((path) => !path.endsWith('.test.tsx'))
  .map((path) => ({ path, source: readFileSync(path, 'utf8') }))

/** Every `targets: ['.foo']` selector declared in a page, with its page. */
const declared = pages.flatMap(({ path, source }) =>
  [...source.matchAll(/targets:\s*\[([^\]]*)\]/g)]
    .flatMap((match) => [...match[1].matchAll(/'\.([A-Za-z0-9_-]+)'/g)])
    .map((match) => ({ path, className: match[1] })),
)

describe('walkthrough targets', () => {
  it('finds tour steps to check', () => {
    expect(declared.length).toBeGreaterThan(0)
  })

  it.each(declared)('$className has a matching class in its own page', ({ path, className }) => {
    const source = pages.find((page) => page.path === path)!.source
    // Whole-token match: a substring check would accept `objective-steps-old`
    // as proof that `objective-steps` still exists.
    const asClass = new RegExp(`(^|[\\s"'\`{}])${className}([\\s"'\`{}]|$)`)
    expect(asClass.test(source), `${className} is not on any element in ${path}`).toBe(true)
  })

  it('mounts a registered tour id on every page that declares steps', () => {
    const pagesWithSteps = new Set(declared.map((target) => target.path))

    pagesWithSteps.forEach((path) => {
      const source = pages.find((page) => page.path === path)!.source
      const mounted = [...source.matchAll(/tourId="([^"]+)"/g)].map((match) => match[1])

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
    // Guided and freeform composers; the engagement's difficulty picks one.
    'objective-compose',
  ])

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
