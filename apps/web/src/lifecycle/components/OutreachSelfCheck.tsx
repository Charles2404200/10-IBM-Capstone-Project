/**
 * The outreach self-check, shown *while* the learner writes.
 *
 * Framing matters more than the heuristics here. This panel is explicitly the
 * learner's own pre-flight check, not a prediction of the client's reaction —
 * presenting it as a score would set up an expectation the server is free to
 * contradict, and being contradicted by a number you were shown is worse than
 * never seeing one. What it does is make the four dimensions the assessment
 * actually uses visible *before* the attempt, so practice can be deliberate.
 */
import { useMemo } from 'react'
import { CheckmarkFilled, CircleDash } from '@carbon/icons-react'
import { evaluateOutreach, type RubricContext } from '../coaching/outreachRubric'
import styles from '../lifecycle.module.scss'

export interface OutreachSelfCheckProps {
  body: string
  context: RubricContext
}

export default function OutreachSelfCheck({ body, context }: OutreachSelfCheckProps) {
  const result = useMemo(() => evaluateOutreach(body, context), [body, context])
  const words = body.trim() === '' ? 0 : body.trim().split(/\s+/).length

  return (
    <section className={styles.consequence} aria-live="polite">
      <p className={styles.consequenceTitle}>
        Self-check — {result.metCount} of 4 · {words} {words === 1 ? 'word' : 'words'}
      </p>
      <p style={{ fontSize: '0.8125rem', color: '#525252', marginBottom: '0.75rem' }}>
        These are the four things the client's team assesses. This is your own check before you
        send — the client still decides.
      </p>

      <ul style={{ display: 'grid', gap: '0.5rem', listStyle: 'none', padding: 0, margin: 0 }}>
        {result.checks.map((check) => (
          <li key={check.dimension} style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-start' }}>
            <span style={{ flexShrink: 0, marginTop: '0.125rem' }} aria-hidden="true">
              {check.met ? (
                <CheckmarkFilled size={16} style={{ fill: '#24a148' }} />
              ) : (
                <CircleDash size={16} style={{ fill: '#8d8d8d' }} />
              )}
            </span>
            <span style={{ fontSize: '0.8125rem', lineHeight: 1.4 }}>
              <strong style={{ color: check.met ? '#161616' : '#525252' }}>{check.label}</strong>
              {!check.met && <span style={{ color: '#525252' }}> — {check.advice}</span>}
              <span className={styles.srOnly}>{check.met ? ' (met)' : ' (not yet met)'}</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
