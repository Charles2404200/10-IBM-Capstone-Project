/**
 * A client-side pre-flight check for outreach emails.
 *
 * The backend scores outreach on four dimensions — personalisation, relevance,
 * clarity and call to action — but the learner only ever sees those scores
 * *after* the client has rejected them. That makes the rubric unusable as a
 * guide: you cannot aim at a target you are only shown once you have missed.
 *
 * This module re-states the same four dimensions as cheap, transparent
 * heuristics that run while the learner types. It is deliberately **not** a
 * prediction of the server's score, and the UI must never present it as one —
 * it is a checklist that makes the standard visible before the attempt, which is
 * the difference between deliberate practice and guessing.
 */

export type RubricDimension = 'personalisation' | 'relevance' | 'clarity' | 'callToAction'

export interface RubricContext {
  /** The stakeholder the learner is writing to, if one has been identified. */
  personaName?: string | null
  /** The client organisation. */
  companyName?: string | null
  /** Salient terms from collected evidence and the client's latest reply. */
  keywords?: string[]
}

export interface RubricCheck {
  dimension: RubricDimension
  label: string
  met: boolean
  /** Shown when the check has not been met — always actionable, never a scold. */
  advice: string
}

export interface RubricResult {
  checks: RubricCheck[]
  /** How many of the four dimensions currently pass. */
  metCount: number
}

const MEETING_WORDS = [
  'call',
  'meet',
  'meeting',
  'chat',
  'conversation',
  'walk you through',
  'diary',
  'calendar',
  'minutes',
  'schedule',
]

const TIME_WORDS = ['minute', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'week', 'next']

function normalise(text: string): string {
  return text.toLowerCase()
}

/** Splits on sentence-ending punctuation, discarding empties. */
export function sentences(text: string): string[] {
  return text
    .split(/[.!?]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
}

export function wordCount(text: string): number {
  const trimmed = text.trim()
    return trimmed.length === 0 ? 0 : trimmed.split(/\s+/).length
}

/** True when the body names the stakeholder or the organisation. */
export function mentionsClient(body: string, context: RubricContext): boolean {
  const haystack = normalise(body)
  const candidates: string[] = []

  if (context.personaName) {
    // Match on the first name too — "Hi Sarah" is personalisation.
    const parts = context.personaName.split(/\s+/).filter(Boolean)
    candidates.push(context.personaName, ...parts.filter((p) => p.length >= 3))
  }
  if (context.companyName) {
    candidates.push(context.companyName)
    // Also accept a distinctive leading word, e.g. "MediCare" from the full name.
    const lead = context.companyName.split(/\s+/)[0]
    if (lead && lead.length >= 4) candidates.push(lead)
  }

  return candidates.some((c) => c.length >= 3 && haystack.includes(normalise(c)))
}

/** True when the body echoes at least one salient term from the evidence. */
export function referencesEvidence(body: string, context: RubricContext): boolean {
  const haystack = normalise(body)
  return (context.keywords ?? []).some(
    (keyword) => keyword.length >= 4 && haystack.includes(normalise(keyword))
  )
}

/**
 * Clarity proxies readability: a body long enough to say something, short enough
 * to be read on a phone, and built from sentences a busy executive can parse.
 */
export function readsClearly(body: string): boolean {
  const words = wordCount(body)
  if (words < 40 || words > 220) return false
  const parts = sentences(body)
  if (parts.length < 2) return false
  const longest = Math.max(...parts.map((s) => wordCount(s)))
  return longest <= 40
}

/**
 * A call to action is a single, concrete, low-friction ask. More than one
 * question mark usually means the reader has to make several decisions, which is
 * the most common reason a first email goes unanswered.
 */
export function hasSingleClearAsk(body: string): boolean {
  const haystack = normalise(body)
  const asksForTime = MEETING_WORDS.some((w) => haystack.includes(w))
  const isConcrete = TIME_WORDS.some((w) => haystack.includes(w)) || /\d/.test(body)
  const questionCount = (body.match(/\?/g) ?? []).length
  return asksForTime && isConcrete && questionCount <= 1
}

export function evaluateOutreach(body: string, context: RubricContext = {}): RubricResult {
  const checks: RubricCheck[] = [
    {
      dimension: 'personalisation',
      label: 'Written to this person',
      met: mentionsClient(body, context),
      advice: context.personaName
        ? `Name ${context.personaName} or their organisation — a generic email reads as a mailshot.`
        : 'Name the person or the organisation you are writing to.',
    },
    {
      dimension: 'relevance',
      label: 'Grounded in what you found',
      met: referencesEvidence(body, context),
      advice: 'Refer to something specific you uncovered about them, not a general capability pitch.',
    },
    {
      dimension: 'clarity',
      label: 'Short enough to be read',
      met: readsClearly(body),
      advice: 'Aim for 40–220 words in a few short sentences. Long single sentences lose the reader.',
    },
    {
      dimension: 'callToAction',
      label: 'One easy thing to say yes to',
      met: hasSingleClearAsk(body),
      advice: 'Ask for one concrete next step — a short call, with a length or a day attached.',
    },
  ]

  return { checks, metCount: checks.filter((c) => c.met).length }
}

/**
 * Extracts salient terms from free text (evidence notes, the client's reply) to
 * seed the relevance check. Stop words are stripped so "the" never counts as
 * grounding.
 */
const STOP_WORDS = new Set([
  'about', 'after', 'again', 'their', 'there', 'these', 'those', 'which', 'while', 'would',
  'could', 'should', 'because', 'before', 'being', 'other', 'where', 'with', 'that', 'this',
  'from', 'they', 'have', 'has', 'was', 'were', 'and', 'the', 'for', 'you', 'your', 'our',
  'not', 'but', 'are', 'its', 'it', 'a', 'an', 'of', 'to', 'in', 'on', 'is', 'we', 'us',
  'seems', 'email', 'make', 'sense', 'sure', 'trying', 'jumble', 'characters', 'doesn',
])

/**
 * Pulls a person's name out of the intelligence panel's decision-maker field,
 * which arrives as prose ("Sarah Chen, Chief Information Officer appears to be
 * the most relevant stakeholder…"). Returns null rather than guessing when the
 * field does not start with something name-shaped.
 */
export function stakeholderNameFrom(value: string | null | undefined): string | null {
  if (!value) return null
  const head = value.split(',')[0].trim()
  if (head.length === 0 || head.length > 60) return null
  const words = head.split(/\s+/)
  if (words.length < 2 || words.length > 4) return null
  // Every word must look like a capitalised name, not a sentence fragment.
  if (!words.every((w) => /^[A-Z][A-Za-z'’-]*$/.test(w))) return null
  return head
}

export function keywordsFrom(texts: Array<string | null | undefined>, limit = 24): string[] {
  const counts = new Map<string, number>()
  for (const text of texts) {
    if (!text) continue
    for (const raw of text.toLowerCase().split(/[^a-z0-9-]+/)) {
      if (raw.length < 5 || STOP_WORDS.has(raw)) continue
      counts.set(raw, (counts.get(raw) ?? 0) + 1)
    }
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, limit)
    .map(([word]) => word)
}
