import { describe, expect, it } from 'vitest'
import {
  assessDraftSafety,
  evaluateOutreach,
  hasSingleClearAsk,
  keywordsFrom,
  mentionsClient,
  readsClearly,
  referencesEvidence,
  sentences,
  stakeholderNameFrom,
  wordCount,
} from './outreachRubric'

const CONTEXT = {
  personaName: 'Sarah Chen',
  companyName: 'MediCare Regional Hospital Network',
  keywords: ['regulatory', 'migration', 'records'],
}

const GOOD_EMAIL = [
  'Hi Sarah, I have been reading about the regulatory audit MediCare has coming up.',
  'The thing that stood out to me is that the 2022 migration left your teams wary of',
  'another platform programme, which is a fair reaction.',
  'We have run staged consolidations for networks your size without freezing clinical work.',
  'Would 20 minutes next week be useful to walk through how we would sequence it?',
].join(' ')

describe('text helpers', () => {
  it('counts words, ignoring surrounding whitespace', () => {
    expect(wordCount('')).toBe(0)
    expect(wordCount('   ')).toBe(0)
    expect(wordCount('one two three')).toBe(3)
    expect(wordCount('  padded  words  ')).toBe(2)
  })

  it('splits sentences on terminal punctuation', () => {
    expect(sentences('One. Two! Three?')).toEqual(['One', 'Two', 'Three'])
    expect(sentences('No terminator')).toEqual(['No terminator'])
    expect(sentences('...')).toEqual([])
  })
})

describe('personalisation', () => {
  it('accepts the stakeholder first name alone', () => {
    expect(mentionsClient('Hi Sarah, quick question.', CONTEXT)).toBe(true)
  })

  it('accepts a distinctive leading word of the company name', () => {
    expect(mentionsClient('I saw the MediCare announcement.', CONTEXT)).toBe(true)
  })

  it('is case-insensitive', () => {
    expect(mentionsClient('hi sarah chen', CONTEXT)).toBe(true)
  })

  it('rejects a generic pitch', () => {
    expect(mentionsClient('Dear Sir or Madam, we deliver digital transformation.', CONTEXT)).toBe(false)
  })

  it('does not crash without context', () => {
    expect(mentionsClient('anything at all', {})).toBe(false)
  })
})

describe('relevance', () => {
  it('passes when the body echoes an evidence keyword', () => {
    expect(referencesEvidence('Your regulatory deadline is the pressure point.', CONTEXT)).toBe(true)
  })

  it('fails on a capability-only pitch', () => {
    expect(referencesEvidence('We are a leading global consultancy.', CONTEXT)).toBe(false)
  })

  it('ignores keywords shorter than four characters', () => {
    expect(referencesEvidence('a b c', { keywords: ['abc'] })).toBe(false)
  })
})

describe('clarity', () => {
  it('accepts a short multi-sentence email', () => {
    expect(readsClearly(GOOD_EMAIL)).toBe(true)
  })

  it('rejects something too short to say anything', () => {
    expect(readsClearly('Hi. Call me.')).toBe(false)
  })

  it('rejects an essay', () => {
    expect(readsClearly(`${'word '.repeat(240)}. And another sentence.`)).toBe(false)
  })

  it('rejects one enormous run-on sentence', () => {
    expect(readsClearly(`${'word '.repeat(60)}.`)).toBe(false)
  })

  it('requires at least two sentences', () => {
    expect(readsClearly(`${'word '.repeat(45)}.`)).toBe(false)
  })
})

describe('call to action', () => {
  it('accepts one concrete, time-bound ask', () => {
    expect(hasSingleClearAsk('Would 20 minutes next week be useful?')).toBe(true)
  })

  it('rejects an ask with no concrete anchor', () => {
    expect(hasSingleClearAsk('Shall we have a chat sometime?')).toBe(false)
  })

  it('rejects an email with no ask at all', () => {
    expect(hasSingleClearAsk('Here is a summary of our capabilities in this area.')).toBe(false)
  })

  it('rejects piling several questions on the reader', () => {
    expect(
      hasSingleClearAsk('Do you have 20 minutes next week? Or shall we meet? Or a call?')
    ).toBe(false)
  })
})

describe('evaluateOutreach', () => {
  it('passes all four dimensions on a well-formed email', () => {
    const result = evaluateOutreach(GOOD_EMAIL, CONTEXT)
    expect(result.metCount).toBe(4)
    expect(result.checks.every((c) => c.met)).toBe(true)
  })

  it('fails everything on an empty body without throwing', () => {
    const result = evaluateOutreach('', CONTEXT)
    expect(result.metCount).toBe(0)
    expect(result.checks).toHaveLength(4)
  })

  it('fails everything on keyboard mash — the exact case that stalls learners', () => {
    const result = evaluateOutreach('m,bj,n,n,n,mn,n,mn,mn,mn,mn,mn,mn,mn,mn,mn', CONTEXT)
    expect(result.metCount).toBe(0)
  })

  it('always returns advice for a failed check', () => {
    for (const check of evaluateOutreach('', CONTEXT).checks) {
      expect(check.advice.length).toBeGreaterThan(10)
    }
  })

  it('names the stakeholder in its personalisation advice when one is known', () => {
    const check = evaluateOutreach('', CONTEXT).checks[0]
    expect(check.advice).toContain('Sarah Chen')
  })

  it('degrades to a generic hint when no stakeholder is known', () => {
    const check = evaluateOutreach('', {}).checks[0]
    expect(check.advice).not.toContain('undefined')
  })
})

describe('draft safety', () => {
  it('blocks abusive language before the learner sends', () => {
    expect(assessDraftSafety('This is bullshit and you are wasting my time.').risk).toBe('blocking')
  })

  it('warns when a draft does not communicate a meaningful reason', () => {
    expect(assessDraftSafety('hello').risk).toBe('warning')
    expect(assessDraftSafety('asdfasdfasdf').risk).toBe('warning')
  })

  it('leaves a substantive professional message unblocked', () => {
    expect(assessDraftSafety(GOOD_EMAIL).risk).toBe('clear')
  })
})

describe('stakeholderNameFrom', () => {
  it('extracts the name from the intelligence panel prose', () => {
    expect(
      stakeholderNameFrom(
        'Sarah Chen, Chief Information Officer appears to be the most relevant stakeholder.'
      )
    ).toBe('Sarah Chen')
  })

  it('accepts a bare name', () => {
    expect(stakeholderNameFrom('Sarah Chen')).toBe('Sarah Chen')
  })

  it('returns null for junk the learner typed', () => {
    expect(stakeholderNameFrom('lk')).toBeNull()
    expect(stakeholderNameFrom('k')).toBeNull()
  })

  it('returns null for a lower-case sentence fragment', () => {
    expect(stakeholderNameFrom('the person who signs things off')).toBeNull()
  })

  it('returns null for empty input', () => {
    expect(stakeholderNameFrom(null)).toBeNull()
    expect(stakeholderNameFrom(undefined)).toBeNull()
    expect(stakeholderNameFrom('')).toBeNull()
  })

  it('rejects a single word, which is more likely a label than a name', () => {
    expect(stakeholderNameFrom('Unknown')).toBeNull()
  })
})

describe('keywordsFrom', () => {
  it('extracts salient terms and drops stop words', () => {
    const words = keywordsFrom(['The regulatory audit and the migration programme'])
    expect(words).toContain('regulatory')
    expect(words).toContain('migration')
    expect(words).not.toContain('the')
    expect(words).not.toContain('and')
  })

  it('ranks more frequent terms first', () => {
    const words = keywordsFrom(['migration migration records'])
    expect(words[0]).toBe('migration')
  })

  it('tolerates null and undefined entries', () => {
    expect(() => keywordsFrom([null, undefined, 'evidence'])).not.toThrow()
  })

  it('honours the limit', () => {
    const many = Array.from({ length: 50 }, (_, i) => `keyword${i}aaa`).join(' ')
    expect(keywordsFrom([many], 5)).toHaveLength(5)
  })

  it('ignores the client rejection boilerplate so it cannot be gamed', () => {
    const words = keywordsFrom([
      "This email seems to be a jumble of characters and doesn't make any sense.",
    ])
    expect(words).not.toContain('jumble')
    expect(words).not.toContain('characters')
  })
})
