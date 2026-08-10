/**
 * The "Brief" beat of every phase: what this step is for, what finishing it
 * looks like, and what it unlocks.
 *
 * Three sentences at the top of a workspace, in the same place on every phase.
 * The cost is one strip of text; the benefit is that a learner never again has
 * to infer the goal of a screen from the shape of its form.
 */
import type { EngagementPhase } from '@/api/types'
import { PHASE_BRIEF } from '../state/progression'
import styles from '../styles/game.module.scss'

export interface PhaseBriefProps {
  phase: EngagementPhase
}

export default function PhaseBrief({ phase }: PhaseBriefProps) {
  const brief = PHASE_BRIEF[phase]
  if (!brief) return null

  return (
    <section className={styles.brief} aria-label="What this step is for">
      <div className={styles.briefCell}>
        <p className={styles.briefLabel}>What this step is for</p>
        <p className={styles.briefText}>{brief.goal}</p>
      </div>
      <div className={styles.briefCell}>
        <p className={styles.briefLabel}>You are done when</p>
        <p className={styles.briefText}>{brief.done}</p>
      </div>
      <div className={styles.briefCell}>
        <p className={styles.briefLabel}>Then</p>
        <p className={styles.briefText}>{brief.next}</p>
      </div>
    </section>
  )
}
