import { Button, Heading, InlineLoading, Tag } from '@carbon/react'
import { Chat, Renew, Send } from '@carbon/icons-react'
import { useNavigate } from 'react-router-dom'
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
  const presentation = outcomePresentation(proposal.clientDecisionOutcome)
  const strengths = decisionInsights(proposal.decisionInsights, 'STRENGTH')
  const concerns = decisionInsights(proposal.decisionInsights, 'CONCERN')
  const conditions = decisionInsights(proposal.decisionInsights, 'CONDITION')
  const supportCounts = proposal.evidenceImpacts.reduce<Record<string, number>>((counts, impact) => {
    counts[impact.supportLevel] = (counts[impact.supportLevel] ?? 0) + 1
    return counts
  }, {})

  return (
    <main className={styles.outcomePage}>
      <p className={styles.eyebrow}>Client decision</p>
      <Heading>Proposal outcome</Heading>
      <p className={styles.subtitle}>The client decision and learning performance are separate, so the outcome stays explainable.</p>

      <section className={styles.outcomeHero}>
        <Tag type={presentation.tagType} size="lg">{presentation.label}</Tag>
        <h2>{presentation.subtitle}</h2>
        <div className={styles.outcomeStats}>
          <div><span>Decision confidence</span><strong>{proposal.decisionConfidence}%</strong></div>
          <div><span>Learner performance</span><strong>{proposal.learnerPerformanceScore}/100</strong></div>
        </div>
      </section>

      <div className={styles.outcomeGrid}>
        <OutcomeList title="Why the client decided this" items={strengths} empty="No material strengths were recorded." />
        <OutcomeList title="What weakened the proposal" items={concerns} empty="No material concerns were recorded." tone="concern" />
        <OutcomeList title="Approval conditions" items={conditions} empty="No additional conditions were recorded." tone="condition" />

        <section className={styles.outcomePanel}>
          <p className={styles.eyebrow}>Client response</p>
          <p className={styles.clientQuote}>{proposal.clientResponse || proposal.decisionRationale}</p>
        </section>

        <section className={styles.outcomePanel}>
          <p className={styles.eyebrow}>Decision score</p>
          <div className={styles.decisionDimensions}>
            {proposal.decisionDimensions.map((dimension) => (
              <div key={dimension.dimension}>
                <div><strong>{dimension.dimension}</strong><span>{dimension.score}/100</span></div>
                <div className={styles.scoreBar}><i style={{ width: `${dimension.score}%` }} /></div>
                <p>{dimension.interpretation}</p>
              </div>
            ))}
          </div>
        </section>

        <section className={styles.outcomePanel}>
          <p className={styles.eyebrow}>Evidence impact</p>
          <div className={styles.evidenceCounts}>
            <span>Supported <strong>{supportCounts.WELL_SUPPORTED ?? 0}</strong></span>
            <span>Partial <strong>{supportCounts.PARTIALLY_SUPPORTED ?? 0}</strong></span>
            <span>Unsupported <strong>{supportCounts.UNSUPPORTED ?? 0}</strong></span>
          </div>
          {proposal.evidenceImpacts.length ? <ul className={styles.impactList}>{proposal.evidenceImpacts.map((impact) => (
            <li key={`${impact.supportLevel}-${impact.claim}`}><Tag type={impact.supportLevel === 'WELL_SUPPORTED' ? 'green' : impact.supportLevel === 'PARTIALLY_SUPPORTED' ? 'warm-gray' : 'red'}>{impact.supportLevel.replace('_', ' ')}</Tag><strong>{impact.claim}</strong><p>{impact.explanation}</p></li>
          ))}</ul> : <p className={styles.empty}>This proposal predates detailed evidence-impact tracking.</p>}
        </section>
      </div>

      <section className={styles.outcomeActions}>
        <div>
          <p className={styles.eyebrow}>Recommended next step</p>
          <h2>{presentation.nextAction}</h2>
        </div>
        <Button renderIcon={Send} onClick={() => navigate(`/dashboard/engagements/${engagementId}/assessment`)}>View full assessment</Button>
        <Button kind="tertiary" renderIcon={Chat} onClick={() => explain.mutate()} disabled={explain.isPending}>Explain decision</Button>
        <Button kind="ghost" renderIcon={Renew} onClick={() => counterfactual.mutate()} disabled={counterfactual.isPending}>What could change?</Button>
      </section>
      {(explain.isPending || counterfactual.isPending) && <InlineLoading description="Preparing decision coaching" />}
      {explain.data && <DecisionNarrative title="Decision explanation" message={explain.data.message} />}
      {counterfactual.data && <DecisionNarrative title="Counterfactual analysis" message={counterfactual.data.message} />}
    </main>
  )
}

function OutcomeList({ title, items, empty, tone }: { title: string; items: Proposal['decisionInsights']; empty: string; tone?: 'concern' | 'condition' }) {
  return <section className={styles.outcomePanel}>
    <p className={styles.eyebrow}>{title}</p>
    {items.length ? <ul className={tone ? styles[tone] : styles.strength}>{items.map((item) => <li key={item.detail}>{item.detail}</li>)}</ul> : <p className={styles.empty}>{empty}</p>}
  </section>
}

function DecisionNarrative({ title, message }: { title: string; message: string }) {
  return <section className={styles.decisionNarrative}><p className={styles.eyebrow}>{title}</p><p>{message}</p></section>
}
