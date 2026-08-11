import { Button, Heading, InlineLoading, InlineNotification, NumberInput, Select, SelectItem, Stack, Tag, TextArea, TextInput } from '@carbon/react'
import { Add, Checkmark, Renew, Send, TrashCan, WarningAlt } from '@carbon/icons-react'
import { useParams } from 'react-router-dom'
import type { ProposalReview } from '@/api/types'
import type { ProposalDraftRequest } from '@/api/hooks/useProposal'
import LoadingState from '@/components/shared/LoadingState'
import { ProposalOutcomeView } from '@/features/proposal/components/ProposalOutcomeView'
import { useProposalStudio } from '@/features/proposal/hooks/useProposalStudio'
import { proposalSections } from '@/features/proposal/services/proposalDraftService'
import { getProblemDetail } from '@/api/problemDetails'
import styles from './ProposalStudioPage.module.scss'
import { PHASE_LABEL } from '@/lifecycle/phases'

export default function ProposalStudioPage() {
  const { engagementId = '' } = useParams<{ engagementId: string }>()
  const studio = useProposalStudio(engagementId)

  if (studio.workspace.isLoading) return <LoadingState />
  if (studio.workspace.isError) {
    return <InlineNotification kind="error" title="Proposal workspace unavailable" subtitle="Please return to the Command Centre and reopen this engagement." hideCloseButton />
  }
  if (studio.submitted && studio.proposal) return <ProposalOutcomeView proposal={studio.proposal} engagementId={engagementId} />

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>Evidence-grounded proposal</p>
          <Heading>{PHASE_LABEL.PROPOSAL}</Heading>
          <p className={styles.subtitle}>Turn research and discovery into a client-ready recommendation. AI reviews your thinking; it does not write the proposal for you.</p>
        </div>
        <div className={styles.headerActions}>
          <SaveStatus state={studio.saveState} />
          <Button kind="tertiary" renderIcon={Renew} onClick={() => void studio.reviewCurrentDraft()} disabled={studio.reviewProposal.isPending || studio.saveDraft.isPending}>Review proposal</Button>
          <Button renderIcon={Send} onClick={() => void studio.submit()} disabled={studio.submitProposal.isPending || studio.reviewProposal.isPending || studio.saveDraft.isPending}>Submit to client</Button>
        </div>
      </header>

      {(studio.submitProposal.isError || studio.saveState === 'error') && <InlineNotification kind="error" title="Proposal could not be saved or submitted" subtitle={getProblemDetail(studio.submitProposal.error ?? studio.saveDraft.error, 'Your draft remains in this workspace. Resolve the highlighted findings and try again.')} hideCloseButton />}

      <div className={styles.workspace}>
        <aside className={styles.sourcesPanel} aria-label="Grounded client sources">
          <div className={styles.panelTitle}><div><p className={styles.eyebrow}>Grounded context</p><h2>Client sources</h2></div><Tag type="blue">{studio.workspace.data?.sources.length ?? 0} available</Tag></div>
          <p className={styles.panelHint}>Attach evidence to the section you are editing. Sources remain traceable in the final proposal.</p>
          <div className={styles.sourceList}>
            {studio.workspace.data?.sources.map((source) => {
              const attached = studio.attachedSourceIds.has(source.id)
              return <article className={styles.source} key={source.id}>
                <div className={styles.sourceMeta}><Tag type={source.type === 'MEETING_DISCOVERY' ? 'purple' : 'cool-gray'}>{source.type === 'MEETING_DISCOVERY' ? 'Meeting' : 'Evidence'}</Tag><span>{source.reliability}</span></div>
                <h3>{source.label}</h3><p>{source.content}</p>
                <Button kind={attached ? 'secondary' : 'ghost'} size="sm" renderIcon={attached ? Checkmark : Add} onClick={() => attached ? studio.detach(source.id) : studio.attach(source)}>{attached ? 'Attached' : `Attach to ${proposalSections.find((section) => section.id === studio.activeSection)?.label}`}</Button>
              </article>
            })}
            {studio.workspace.data?.sources.length === 0 && <p className={styles.empty}>No evidence or discovery facts are available yet.</p>}
          </div>
        </aside>

        <section className={styles.builder}>
          <nav className={styles.sectionNav} aria-label="Proposal sections">
            {proposalSections.map((section) => <button type="button" key={section.id} className={studio.activeSection === section.id ? styles.activeTab : styles.tab} onClick={() => studio.setActiveSection(section.id)}>{section.label}{studio.draft.evidenceLinks.some((link) => link.section === section.id) && <span className={styles.dot} />}</button>)}
          </nav>
          <div className={styles.editor}>
            {studio.activeSection === 'PROBLEM' && <Foundation draft={studio.draft} update={studio.updateDraft} />}
            {studio.activeSection === 'OUTCOMES' && <Commercial draft={studio.draft} update={studio.updateDraft} />}
            {studio.activeSection === 'TIMELINE' && <Delivery draft={studio.draft} update={studio.updateDraft} />}
            {studio.activeSection === 'RISKS' && <RiskAssumptions draft={studio.draft} update={studio.updateDraft} showRisks />}
            {studio.activeSection === 'ASSUMPTIONS' && <RiskAssumptions draft={studio.draft} update={studio.updateDraft} showRisks={false} />}
          </div>
        </section>

        <aside className={styles.reviewPanel} aria-label="Proposal validation and coaching">
          <div className={styles.panelTitle}><div><p className={styles.eyebrow}>FactGuard</p><h2>Proposal health</h2></div></div>
          <section className={styles.attachments}><span>Evidence linked</span><strong>{studio.draft.evidenceLinks.length}</strong><span>Client sources</span><strong>{studio.workspace.data?.sources.length ?? 0}</strong></section>
          <Button kind="tertiary" size="sm" renderIcon={Renew} onClick={() => void studio.reviewCurrentDraft()} disabled={studio.reviewProposal.isPending}>Run AI proposal review</Button>
          <Button kind="ghost" size="sm" onClick={() => void studio.challengeCurrentDraft()} disabled={studio.challengeProposal.isPending}>Challenge my proposal</Button>
          {studio.challengeProposal.isPending && <InlineLoading description="Preparing client concerns" />}
          {studio.challengeProposal.data && <section className={styles.coaching}><h3>Client concerns</h3>{studio.challengeProposal.data.concerns.map((concern) => <p key={concern}>{concern}</p>)}</section>}
          {studio.review && <ReviewPanel review={studio.review} />}
          {!studio.review && <p className={styles.empty}>Run a review to check evidence, commercial logic, client fit and delivery risk before submission.</p>}
        </aside>
      </div>
    </main>
  )
}

type DraftUpdate = (fn: (value: ProposalDraftRequest) => ProposalDraftRequest) => void

function Foundation({ draft, update }: { draft: ProposalDraftRequest; update: DraftUpdate }) {
  return <Stack gap={6}><div><h2>Proposal foundation</h2><p>State the client problem and explain why this recommendation addresses it.</p></div><TextArea id="problem-statement" labelText="Problem statement" helperText="Use observed operational, commercial or risk impacts. Attach supporting evidence from the source panel." rows={5} value={draft.problemStatement} onChange={(event) => update((current) => ({ ...current, problemStatement: event.target.value }))} /><TextArea id="solution-strategy" labelText="Recommended solution" helperText="Explain the solution logic, not only a list of technology components." rows={5} value={draft.solutionStrategy} onChange={(event) => update((current) => ({ ...current, solutionStrategy: event.target.value }))} /><ListEditor label="Solution components" values={draft.components} placeholder="e.g. Integration pilot and workflow redesign" onChange={(components) => update((current) => ({ ...current, components }))} /></Stack>
}

function Commercial({ draft, update }: { draft: ProposalDraftRequest; update: DraftUpdate }) {
  return <Stack gap={6}><div><h2>Value and commercial logic</h2><p>Make outcomes measurable and distinguish consultant estimates from confirmed client facts.</p></div><StructuredEditor label="Expected business outcomes and KPIs" addLabel="Add outcome" rows={draft.businessOutcomes} empty={{ outcome: '', metric: '', target: '' }} fields={[['outcome', 'Business outcome'], ['metric', 'Metric'], ['target', 'Target']]} onChange={(businessOutcomes) => update((current) => ({ ...current, businessOutcomes }))} /><div className={styles.commercialGrid}><NumberInput id="proposal-budget" label="Estimated budget (USD)" min={0} value={draft.budget} onChange={(_, data) => update((current) => ({ ...current, budget: String(data.value) }))} /><Select id="budget-confidence" labelText="Confidence" value={draft.budgetConfidence} onChange={(event) => update((current) => ({ ...current, budgetConfidence: event.target.value }))}><SelectItem value="UNCONFIRMED" text="Unconfirmed" /><SelectItem value="LOW" text="Low" /><SelectItem value="MEDIUM" text="Medium" /><SelectItem value="HIGH" text="High" /></Select><TextInput id="budget-source" labelText="Source / basis" value={draft.budgetSource} onChange={(event) => update((current) => ({ ...current, budgetSource: event.target.value }))} /></div></Stack>
}

function Delivery({ draft, update }: { draft: ProposalDraftRequest; update: DraftUpdate }) {
  return <Stack gap={6}><div><h2>Timeline and milestones</h2><p>Translate the delivery window into observable milestones the client can evaluate.</p></div><NumberInput id="timeline-weeks" label="Total timeline (weeks)" min={1} value={draft.timelineWeeks} onChange={(_, data) => update((current) => ({ ...current, timelineWeeks: Number(data.value) || 1 }))} /><StructuredEditor label="Milestones" addLabel="Add milestone" rows={draft.milestones} empty={{ phase: '', duration: '' }} fields={[['phase', 'Phase / milestone'], ['duration', 'Timing']]} onChange={(milestones) => update((current) => ({ ...current, milestones }))} /></Stack>
}

function RiskAssumptions({ draft, update, showRisks }: { draft: ProposalDraftRequest; update: DraftUpdate; showRisks: boolean }) {
  if (!showRisks) return <Stack gap={6}><div><h2>Evidence and assumptions</h2><p>Make the conditions behind the proposal explicit. Evidence attached from the source panel is retained by section.</p></div><ListEditor label="Assumptions and dependencies" values={draft.assumptions} placeholder="e.g. Client SMEs are available for targeted validation" onChange={(assumptions) => update((current) => ({ ...current, assumptions }))} /><EvidenceSummary draft={draft} /></Stack>
  return <Stack gap={6}><div><h2>Risks and mitigations</h2><p>Show the client how delivery, operational and adoption risks will be controlled.</p></div><StructuredEditor label="Risks" addLabel="Add risk" rows={draft.risks} empty={{ risk: '', severity: 'MEDIUM', mitigation: '' }} fields={[['risk', 'Risk'], ['severity', 'Severity'], ['mitigation', 'Mitigation']]} onChange={(risks) => update((current) => ({ ...current, risks }))} /></Stack>
}

function ListEditor({ label, values, placeholder, onChange }: { label: string; values: string[]; placeholder: string; onChange: (values: string[]) => void }) {
  return <section className={styles.editorGroup}><h3>{label}</h3><Stack gap={3}>{values.map((value, index) => <div className={styles.row} key={`${label}-${index}`}><TextInput id={`${label}-${index}`} labelText="" hideLabel placeholder={placeholder} value={value} onChange={(event) => onChange(values.map((entry, position) => position === index ? event.target.value : entry))} /><Button kind="ghost" hasIconOnly iconDescription="Remove item" renderIcon={TrashCan} onClick={() => onChange(values.length === 1 ? [''] : values.filter((_, position) => position !== index))} /></div>)}<Button kind="tertiary" size="sm" renderIcon={Add} onClick={() => onChange([...values, ''])}>Add item</Button></Stack></section>
}

function StructuredEditor<T extends Record<string, string>>({ label, addLabel, rows, empty, fields, onChange }: { label: string; addLabel: string; rows: T[]; empty: T; fields: [keyof T, string][]; onChange: (rows: T[]) => void }) {
  return <section className={styles.editorGroup}><h3>{label}</h3><Stack gap={3}>{rows.map((row, index) => <div className={styles.structuredRow} key={`${label}-${index}`}>{fields.map(([key, fieldLabel]) => key === 'severity' ? <Select key={String(key)} id={`${label}-${index}-${String(key)}`} labelText={fieldLabel} value={row[key]} onChange={(event) => onChange(rows.map((entry, position) => position === index ? { ...entry, [key]: event.target.value } : entry))}><SelectItem value="LOW" text="Low" /><SelectItem value="MEDIUM" text="Medium" /><SelectItem value="HIGH" text="High" /></Select> : <TextInput key={String(key)} id={`${label}-${index}-${String(key)}`} labelText={fieldLabel} value={row[key]} onChange={(event) => onChange(rows.map((entry, position) => position === index ? { ...entry, [key]: event.target.value } : entry))} />)}<Button kind="ghost" hasIconOnly iconDescription="Remove item" renderIcon={TrashCan} onClick={() => onChange(rows.length === 1 ? [empty] : rows.filter((_, position) => position !== index))} /></div>)}<Button kind="tertiary" size="sm" renderIcon={Add} onClick={() => onChange([...rows, empty])}>{addLabel}</Button></Stack></section>
}

function EvidenceSummary({ draft }: { draft: ProposalDraftRequest }) {
  return <section className={styles.evidenceSummary}><h3>Attached sources</h3>{draft.evidenceLinks.length ? <ul>{draft.evidenceLinks.map((link) => <li key={`${link.section}-${link.sourceId}`}>{link.section}: {link.sourceId.startsWith('meeting:') ? 'Meeting discovery' : 'Research evidence'}</li>)}</ul> : <p>No sources attached yet. Return to a proposal section and select a source from the context panel.</p>}</section>
}

function ReviewPanel({ review }: { review: ProposalReview }) {
  const scores = [['Problem definition', review.problemDefinitionScore], ['Evidence grounding', review.evidenceGroundingScore], ['Client alignment', review.clientAlignmentScore], ['Commercial logic', review.commercialLogicScore], ['Risk coverage', review.riskCoverageScore], ['Feasibility', review.feasibilityScore]]
  return <section className={styles.reviewResult}><Tag type={review.readyToSubmit ? 'green' : 'red'}>{review.readyToSubmit ? 'Ready to submit' : 'Action required'}</Tag><h3>AI proposal review</h3>{scores.map(([label, score]) => <div className={styles.score} key={String(label)}><span>{label}</span><strong>{score}/100</strong><div><i style={{ width: `${score}%` }} /></div></div>)}<p className={styles.feedback}>{review.executiveFeedback}</p>{review.validationIssues.map((issue) => <p className={issue.severity === 'BLOCKING' ? styles.blocking : styles.warning} key={issue.code}><WarningAlt size={16} />{issue.message}</p>)}<h3>Client alignment matrix</h3>{review.clientAlignment.map((item) => <div key={item.sourceId} className={styles.alignment}><Tag type={item.coverage === 'STRONG' ? 'green' : item.coverage === 'PARTIAL' ? 'warm-gray' : 'red'}>{item.coverage}</Tag><p><strong>{item.clientPriority}</strong>{item.detail}</p></div>)}<h3>Suggested improvements</h3><ul>{review.improvementActions.map((action) => <li key={action}>{action}</li>)}</ul></section>
}

function SaveStatus({ state }: { state: 'idle' | 'saving' | 'saved' | 'error' }) {
  if (state === 'saving') return <InlineLoading description="Saving draft" />
  if (state === 'saved') return <span className={styles.saved}>Draft saved</span>
  if (state === 'error') return <span className={styles.saveError}>Save failed</span>
  return <span className={styles.muted}>Draft changes save automatically</span>
}
