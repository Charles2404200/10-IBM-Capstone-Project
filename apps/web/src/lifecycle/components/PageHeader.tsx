/**
 * The heading block every workspace shares.
 *
 * Before this, each page built its own: some wrapped the title in Carbon's CSS
 * Grid, some in a plain div, some added an eyebrow, and the paddings were all
 * chosen separately. The measurable result was a title that landed anywhere
 * between 104px and 184px down the page, so it jumped as you moved between
 * steps. Standardising the padding did not fix it, because the offset came from
 * which grid row the heading fell into rather than from padding — the fix has
 * to be one block, outside the grid, used by everybody.
 *
 * It deliberately sits outside Carbon's Grid. The grid is for a page's content
 * columns; the title is not one of them.
 */
import type { ReactNode } from 'react'
import { Heading } from '@carbon/react'
import type { EngagementPhase } from '@/api/types'
import { PHASE_LABEL } from '../phases'
import PhaseBrief from './PhaseBrief'
import styles from '../lifecycle.module.scss'

export interface PageHeaderProps {
  /** Titles the page from the shared vocabulary. */
  phase: EngagementPhase
  /** One sentence under the title. Omit where the brief already says it. */
  description?: ReactNode
  /** Buttons or status shown opposite the title. */
  actions?: ReactNode
  /**
   * Show the three-cell brief: what the step is for, when it is done, what it
   * unlocks. Pass `false` once the step is complete — instructions for a
   * finished task are just height in the place the outcome should be.
   */
  brief?: boolean
}

export default function PageHeader({ phase, description, actions, brief = false }: PageHeaderProps) {
  return (
    <header className={styles.pageHeader}>
      <div className={styles.pageHeaderRow}>
        <div className={styles.pageHeaderText}>
          <Heading className={styles.pageTitle}>{PHASE_LABEL[phase]}</Heading>
          {description && <p className={styles.pageDescription}>{description}</p>}
        </div>
        {actions && <div className={styles.pageHeaderActions}>{actions}</div>}
      </div>
      {brief && <PhaseBrief phase={phase} />}
    </header>
  )
}
