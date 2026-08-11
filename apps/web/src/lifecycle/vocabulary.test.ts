/**
 * Guards the single vocabulary.
 *
 * A phase drifted to four different names once already — "Make contact" in the
 * stepper, "Outreach Desk" on the map, "Outreach Workspace" as a page title,
 * "Outreach" in the progress bar — and a first-time learner has no way to know
 * those are one step. It then drifted again when a page kept its own private
 * copy of the whole phase list.
 *
 * The other tests check the values agree. This one checks nobody has quietly
 * written a second source of them, by reading the pages as text.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { PHASE_LABEL } from './phases'

const PAGES_DIR = join(process.cwd(), 'src', 'pages')

/** Names the product used before the vocabulary was unified. */
const RETIRED_NAMES = [
  'Lead Pipeline',
  'Client Intelligence',
  'Outreach Workspace',
  'Meeting Preparation',
  'Proposal Studio',
  'Portfolio & Progression',
  'Portfolio &amp; Progression',
]

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return path.endsWith('.tsx') || path.endsWith('.ts') ? [path] : []
  })
}

/** Strips comments so prose explaining the old name is not mistaken for it. */
function code(path: string): string {
  return readFileSync(path, 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

describe('one vocabulary, enforced across the pages', () => {
  const files = sourceFiles(PAGES_DIR)

  it('finds the page sources to check', () => {
    expect(files.length).toBeGreaterThan(5)
  })

  it('has no page rendering a retired phase name', () => {
    const offenders: string[] = []
    for (const file of files) {
      const text = code(file)
      for (const name of RETIRED_NAMES) {
        // Any occurrence in page source: headings, button labels and prose
        // alike. The first pass only checked headings, and three sentences kept
        // the old names for another round.
        if (text.includes(name)) {
          offenders.push(`${file.split('/pages/')[1]} still says "${name}"`)
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('has no page keeping its own copy of the phase list', () => {
    // A private label map is how the vocabulary drifted the second time.
    const offenders = files.filter((file) => {
      const text = code(file)
      const declaresMap = /(PHASE_LABELS?|PHASE_NAMES?)\s*[:=]/.test(text)
      const importsShared = text.includes("from '@/game/state/progression'")
      return declaresMap && !importsShared
    })
    expect(offenders.map((f) => f.split('/pages/')[1])).toEqual([])
  })

  it('keeps every phase name in use somewhere the learner can read it', () => {
    // Cheap sanity check that the shared module is actually the source: each
    // label should be a plain string, not an identifier or a key.
    for (const [phase, label] of Object.entries(PHASE_LABEL)) {
      expect(label.trim(), `${phase} has an empty label`).not.toBe('')
      expect(label, `${phase} looks like an identifier`).not.toMatch(/^[A-Z_]+$/)
    }
  })
})
