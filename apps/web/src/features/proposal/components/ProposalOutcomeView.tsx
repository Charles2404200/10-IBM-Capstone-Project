import { Button, Heading, InlineLoading, Tag } from '@carbon/react'
import { Chat, CheckmarkFilled, Renew, Send, WarningFilled } from '@carbon/icons-react'
import { useNavigate } from 'react-router-dom'
import { useMemo, useState } from 'react'
import type { Proposal } from '@/api/types'
import {
  useProposalCounterfactual,
  useProposalDecisionExplanation,
} from '@/api/hooks/useProposal'
import { decisionInsights, outcomePresentation } from '../services/proposalOutcomeService'
import styles from '@/pages/ProposalStudio/ProposalStudioPage.module.scss'

export function ProposalOutcomeView({ proposal, engagementId }: { proposal: Proposal; engagementId: string }) {
  const navigate = useNavigate()
  const explain = useProposalDecisionExplanation(engagementId)
  const counterfactual = useProposalCounterfactual(engagementId)
  const [activeView, setActiveView] = useState<'overview' | 'score' | 'evidence'>('overview')
  const [impactPage, setImpactPage] = useState(0)
  const presentation = outcomePresentation(proposal.clientDecisionOutcome)
  const strengths = decisionInsights(proposal.decisionInsights, 'STRENGTH')
  const concerns = decisionInsights(proposal.decisionInsights, 'CONCERN')
  const conditions = decisionInsights(proposal.decisionInsights, 'CONDITION')
  const supportCounts = proposal.evidenceImpacts.reduce<Record<string, number>>((counts, impact) => {
    counts[impact.supportLevel] = (counts[impact.supportLevel] ?? 0) + 1
    return counts
  }, {})
  const visibleImpact = proposal.evidenceImpacts[impactPage]
  const impactPageCount = proposal.evidenceImpacts.length
  const strongestDimension = useMemo(
    () => [...proposal.decisionDimensions].sort((left, right) => right.score - left.score)[0],
    [proposal.decisionDimensions],
  )

  const selectView = (view: 'overview' | 'score' | 'evidence') => {
    setActiveView(view)
  }

  return (
    <main className={styles.outcomePage}>
      <header className={styles.outcomeHeader}>
        <div>
          <p className={styles.eyebrow}>Client decision</p>
          <Heading>Proposal outcome</Heading>
          <p className={styles.subtitle}>A clear view of the client decision, its conditions and your learning result.</p>
        </div>
        <div className={styles.outcomeHeaderStats}>
          <OutcomeStat label="Decision confidence" value={`${proposal.decisionConfidence}%`} />
          <OutcomeStat label="Learner performance" value={`${proposal.learnerPerformanceScore}/100`} />
        </div>
      </header>

      <section className={styles.outcomeCanvas}>
        <aside className={styles.decisionRail}>
          <Tag type={presentation.tagType}>{presentation.label}</Tag>
          <div className={styles.outcomeIcon}><CheckmarkFilled size={28} /></div>
          <h2>{presentation.subtitle}</h2>
          <p className={styles.decisionRationale}>{proposal.decisionRationale}</p>
          <div className={styles.railMetric}>
            <span>Strongest factor</span>
            <strong>{strongestDimension ? `${strongestDimension.dimension} ${strongestDimension.score}/100` : 'Decision recorded'}</strong>
          </div>
        </aside>

        <section className={styles.outcomeWorkspace}>
          <div className={styles.outcomeTabs} role="tablist" aria-label="Proposal outcome detail">
            <OutcomeTab active={activeView === 'overview'} onClick={() => selectView('overview')} label="Decision summary" />
            <OutcomeTab active={activeView === 'score'} onClick={() => selectView('score')} label="Decision score" />
            <OutcomeTab active={activeView === 'evidence'} onClick={() => selectView('evidence')} label={`Evidence impact (${proposal.evidenceImpacts.length})`} />
          </div>

          <div className={styles.outcomeView}>
            {activeView === 'overview' && <DecisionOverview strengths={strengths} concerns={concerns} conditions={conditions} clientResponse={proposal.clientResponse || proposal.decisionRationale} />}
            {activeView === 'score' && <DecisionScore dimensions={proposal.decisionDimensions} />}
            {activeView === 'evidence' && <EvidenceImpact impact={visibleImpact} current={impactPage} total={impactPageCount} counts={supportCounts} onPrevious={() => setImpactPage((current) => Math.max(0, current - 1))} onNext={() => setImpactPage((current) => Math.min(impactPageCount - 1, current + 1))} />}
          </div>
        </section>

        <aside className={styles.actionRail}>
          <div className={styles.nextStep}>
            <p className={styles.eyebrow}>Recommended next step</p>
            <h2>{presentation.nextAction}</h2>
            <Button renderIcon={Send} onClick={() => navigate(`/dashboard/engagements/${engagementId}/assessment`)}>View full assessment</Button>
          </div>

          <section className={styles.decisionCoach}>
            <p className={styles.eyebrow}>Decision coach</p>
            <h3>{explain.data ? 'Decision explanation' : counterfactual.data ? 'What could have changed' : 'Understand the outcome'}</h3>
            {(explain.isPending || counterfactual.isPending) ? <InlineLoading description="Preparing decision coaching" /> : <p>{explain.data?.message ?? counterfactual.data?.message ?? 'Review the decision score and evidence impact, then open a focused coaching view when you need it.'}</p>}
            <div className={styles.coachActions}>
              <Button kind="tertiary" size="sm" renderIcon={Chat} onClick={() => explain.mutate()} disabled={explain.isPending || counterfactual.isPending}>Explain decision</Button>
              <Button kind="ghost" size="sm" renderIcon={Renew} onClick={() => counterfactual.mutate()} disabled={explain.isPending || counterfactual.isPending}>What could change?</Button>
            </div>
          </section>
        </aside>
      </section>
    </main>
  )
}

function OutcomeStat({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}

function OutcomeTab({ active, label, onClick }: { active: boolean; label: string; onClick: () => void }) {
  return <button type="button" role="tab" aria-selected={active} className={active ? styles.outcomeTabActive : styles.outcomeTab} onClick={onClick}>{label}</button>
}

function DecisionOverview({ strengths, concerns, conditions, clientResponse }: { strengths: Proposal['decisionInsights']; concerns: Proposal['decisionInsights']; conditions: Proposal['decisionInsights']; clientResponse: string }) {
  return <div className={styles.summaryView}>
    <section className={styles.summaryCard}><p className={styles.eyebrow}>Why it moved forward</p><InsightList items={strengths} empty="No material strengths were recorded." tone="positive" /></section>
    <section className={styles.summaryCard}><p className={styles.eyebrow}>Conditions to carry forward</p><InsightList items={conditions} empty="No additional conditions were recorded." tone="neutral" /></section>
    <section className={`${styles.summaryCard} ${styles.concernCard}`}><p className={styles.eyebrow}>Watch-outs</p><InsightList items={concerns} empty="No material concerns were recorded." tone="warning" /></section>
    <section className={`${styles.summaryCard} ${styles.clientResponseCard}`}><p className={styles.eyebrow}>What the client said</p><p>{clientResponse}</p></section>
  </div>
}

function InsightList({ items, empty, tone }: { items: Proposal['decisionInsights']; empty: string; tone: 'positive' | 'neutral' | 'warning' }) {
  if (!items.length) return <p className={styles.empty}>{empty}</p>
  return <ul className={styles[`insight${tone[0].toUpperCase()}${tone.slice(1)}`]}>{items.slice(0, 3).map((item) => <li key={item.detail}>{item.detail}</li>)}</ul>
}

function DecisionScore({ dimensions }: { dimensions: Proposal['decisionDimensions'] }) {
  return <section className={styles.scoreView}>
    <div className={styles.scoreSummary}><p className={styles.eyebrow}>Deterministic decision score</p><p>Client outcome is calculated from scenario rules, evidence and relationship state. AI explains the result; it does not decide it.</p></div>
    <div className={styles.dimensionGrid}>{dimensions.map((dimension) => <article key={dimension.dimension} className={styles.dimensionCard}><div><strong>{dimension.dimension}</strong><span>{dimension.score}/100</span></div><div className={styles.scoreBar}><i style={{ width: `${dimension.score}%` }} /></div><p>{dimension.interpretation}</p></article>)}</div>
  </section>
}

function EvidenceImpact({ impact, current, total, counts, onPrevious, onNext }: { impact: Proposal['evidenceImpacts'][number] | undefined; current: number; total: number; counts: Record<string, number>; onPrevious: () => void; onNext: () => void }) {
  return <section className={styles.evidenceView}>
    <div className={styles.evidenceCountGrid}>
      <EvidenceCount label="Well supported" value={counts.WELL_SUPPORTED ?? 0} tone="green" />
      <EvidenceCount label="Partially supported" value={counts.PARTIALLY_SUPPORTED ?? 0} tone="gray" />
      <EvidenceCount label="Unsupported" value={counts.UNSUPPORTED ?? 0} tone="red" />
    </div>
    {impact ? <article className={styles.impactCard}>
      <div className={styles.impactHeader}><Tag type={impact.supportLevel === 'WELL_SUPPORTED' ? 'green' : impact.supportLevel === 'PARTIALLY_SUPPORTED' ? 'warm-gray' : 'red'}>{impact.supportLevel.replace('_', ' ')}</Tag><span>{current + 1} of {total}</span></div>
      <h3>{impact.claim}</h3><p>{impact.explanation}</p>
      {total > 1 && <div className={styles.impactPager}><Button kind="ghost" size="sm" disabled={current === 0} onClick={onPrevious}>Previous</Button><Button kind="tertiary" size="sm" disabled={current === total - 1} onClick={onNext}>Next claim</Button></div>}
    </article> : <div className={styles.emptyImpact}><WarningFilled size={20} /><p>This proposal predates detailed evidence-impact tracking.</p></div>}
  </section>
}

function EvidenceCount({ label, value, tone }: { label: string; value: number; tone: 'green' | 'gray' | 'red' }) {
  return <div className={styles[`evidenceCount${tone[0].toUpperCase()}${tone.slice(1)}`]}><span>{label}</span><strong>{value}</strong></div>
}
