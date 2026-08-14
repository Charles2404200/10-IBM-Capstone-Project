import { useEffect, useState } from 'react'
import { Button, Heading, InlineLoading, InlineNotification, NumberInput, Select, SelectItem, Stack, Tag, TextArea, TextInput } from '@carbon/react'
import { Add, Checkmark, CheckmarkFilled, ChevronLeft, ChevronRight, Renew, Send, TrashCan, WarningAlt } from '@carbon/icons-react'
import { useParams } from 'react-router-dom'
import type { ProposalReview } from '@/api/types'
import type { ProposalDraftRequest } from '@/api/hooks/useProposal'
import LoadingState from '@/components/shared/LoadingState'
import { ProposalOutcomeView } from '@/features/proposal/components/ProposalOutcomeView'
import { useProposalStudio } from '@/features/proposal/hooks/useProposalStudio'
import { proposalSections } from '@/features/proposal/services/proposalDraftService'
import { PHASE_LABEL } from '@/lifecycle/phases'
import { getProblemDetail } from '@/api/problemDetails'
import styles from './ProposalStudioPage.module.scss'

const SOURCES_PER_PAGE = 3

export default function ProposalStudioPage() {
  const { engagementId = '' } = useParams<{ engagementId: string }>()
  const studio = useProposalStudio(engagementId)
  const [sourcePage, setSourcePage] = useState(0)
  const sources = studio.workspace.data?.sources ?? []
  const sourcePageCount = Math.max(1, Math.ceil(sources.length / SOURCES_PER_PAGE))
  const visibleSources = sources.slice(sourcePage * SOURCES_PER_PAGE, (sourcePage + 1) * SOURCES_PER_PAGE)
  const sourceStart = sources.length === 0 ? 0 : sourcePage * SOURCES_PER_PAGE + 1
  const sourceEnd = Math.min(sources.length, (sourcePage + 1) * SOURCES_PER_PAGE)
  const isReviewing = studio.reviewProposal.isPending
  const isSubmitting = studio.submitProposal.isPending

  useEffect(() => {
    setSourcePage((current) => Math.min(current, sourcePageCount - 1))
  }, [sourcePageCount])

  if (studio.workspace.isLoading) return <LoadingState />
  if (studio.workspace.isError) {
    return <InlineNotification kind="error" title="Proposal workspace unavailable" subtitle="Please return to the Command Centre and reopen this engagement." hideCloseButton />
  }
  if (studio.submitted && studio.proposal) return <ProposalOutcomeView proposal={studio.proposal} engagementId={engagementId} />

  return (
    <main className={styles.page}>
      <div className={styles.canvas}>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>Evidence-grounded proposal</p>
          <Heading>{PHASE_LABEL.PROPOSAL}</Heading>
          <p className={styles.subtitle}>Build a concise recommendation from client evidence. The coach reviews your reasoning; it never writes the proposal for you.</p>
        </div>
        <div className={styles.headerActions}>
          {isReviewing ? <InlineLoading description="Reviewing proposal" /> : isSubmitting ? <InlineLoading description="Submitting to client" /> : <SaveStatus state={studio.saveState} />}
          <Button kind="tertiary" renderIcon={Renew} onClick={() => void studio.reviewCurrentDraft()} disabled={isReviewing || isSubmitting || studio.saveDraft.isPending}>{isReviewing ? 'Reviewing proposal' : 'Review proposal'}</Button>
          <Button renderIcon={Send} onClick={() => void studio.submit()} disabled={isSubmitting || isReviewing || studio.saveDraft.isPending}>{isSubmitting ? 'Submitting to client' : 'Submit to client'}</Button>
        </div>
      </header>

      {(studio.submitProposal.isError || studio.saveState === 'error') && <InlineNotification kind="error" title="Proposal could not be saved or submitted" subtitle={getProblemDetail(studio.submitProposal.error ?? studio.saveDraft.error, 'Your draft remains in this workspace. Resolve the highlighted findings and try again.')} hideCloseButton />}

      <section className={styles.progressStrip} aria-label="Proposal progress">
        {proposalSections.map((section, index) => {
          const isActive = studio.activeSection === section.id
          const linked = studio.draft.evidenceLinks.some((link) => link.section === section.id)
          return <button type="button" key={section.id} className={isActive ? styles.progressStepActive : styles.progressStep} onClick={() => studio.setActiveSection(section.id)}>
            <span>{linked ? <CheckmarkFilled /> : index + 1}</span>
            <strong>{section.label}</strong>
          </button>
        })}
      </section>

      <div className={styles.workspace}>
        <aside className={styles.sourcesPanel} aria-label="Grounded client sources">
          <div className={styles.panelTitle}><div><p className={styles.eyebrow}>Grounded context</p><h2>Evidence library</h2></div><Tag type="blue">{sources.length} sources</Tag></div>
          <div className={styles.sourceToolbar}>
            <p className={styles.panelHint}>Showing {sourceStart}-{sourceEnd} of {sources.length} for <strong>{proposalSections.find((section) => section.id === studio.activeSection)?.label}</strong>.</p>
            {sourcePageCount > 1 && <div className={styles.sourcePagination} aria-label="Evidence source pages"><Button kind="ghost" size="sm" hasIconOnly renderIcon={ChevronLeft} iconDescription="Previous sources" disabled={sourcePage === 0} onClick={() => setSourcePage((current) => current - 1)} /><span aria-live="polite">{sourcePage + 1}/{sourcePageCount}</span><Button kind="ghost" size="sm" hasIconOnly renderIcon={ChevronRight} iconDescription="Next sources" disabled={sourcePage === sourcePageCount - 1} onClick={() => setSourcePage((current) => current + 1)} /></div>}
          </div>
          <div className={styles.sourceList}>
            {visibleSources.map((source) => {
              const attached = studio.attachedSourceIds.has(source.id)
              return <article className={styles.source} key={source.id}>
                <div className={styles.sourceMeta}><Tag type={source.type === 'MEETING_DISCOVERY' ? 'purple' : 'cool-gray'}>{source.type === 'MEETING_DISCOVERY' ? 'Meeting' : 'Evidence'}</Tag><span>{source.reliability}</span></div>
                <h3>{source.label}</h3><p>{source.content}</p>
                <Button kind={attached ? 'secondary' : 'tertiary'} size="sm" renderIcon={attached ? Checkmark : Add} onClick={() => attached ? studio.detach(source.id) : studio.attach(source)}>{attached ? 'Attached' : 'Attach source'}</Button>
              </article>
            })}
            {sources.length === 0 && <p className={styles.empty}>No evidence or discovery facts are available yet.</p>}
          </div>
        </aside>

        <section className={styles.builder}>
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
          <section className={styles.attachments}><span>Evidence linked</span><strong>{studio.draft.evidenceLinks.length}</strong><span>Sections grounded</span><strong>{new Set(studio.draft.evidenceLinks.map((link) => link.section)).size}/5</strong></section>
          <Button kind="tertiary" size="sm" renderIcon={Renew} onClick={() => void studio.reviewCurrentDraft()} disabled={isReviewing || isSubmitting || studio.saveDraft.isPending}>{isReviewing ? 'Reviewing proposal' : 'Run AI proposal review'}</Button>
          {isReviewing && <section className={styles.reviewLoading} role="status" aria-live="polite"><div><strong>Checking your proposal</strong><span>Validating evidence, client alignment and delivery risk.</span></div><div className={styles.reviewLoadingTrack}><i /></div></section>}
          {isSubmitting && <section className={styles.reviewLoading} role="status" aria-live="polite"><div><strong>Applying the client decision</strong><span>Persisting the deterministic outcome. The client narrative will continue in the background.</span></div><div className={styles.reviewLoadingTrack}><i /></div></section>}
          <Button kind="ghost" size="sm" onClick={() => void studio.challengeCurrentDraft()} disabled={studio.challengeProposal.isPending || isReviewing || isSubmitting}>Challenge my proposal</Button>
          {studio.challengeProposal.isPending && <InlineLoading description="Preparing client concerns" />}
          {studio.challengeProposal.data && <section className={styles.coaching}><h3>Client concern</h3><p>{studio.challengeProposal.data.concerns[0]}</p></section>}
          {studio.review && <ReviewPanel review={studio.review} />}
          {!studio.review && <section className={styles.coaching}><h3>Next best action</h3><p>{studio.draft.evidenceLinks.length ? 'Write the current section using the evidence you attached, then run a review.' : 'Attach one source to the current section so the proposal stays traceable.'}</p></section>}
        </aside>
      </div>
      </div>
    </main>
  )
}

type DraftUpdate = (fn: (value: ProposalDraftRequest) => ProposalDraftRequest) => void

function Foundation({ draft, update }: { draft: ProposalDraftRequest; update: DraftUpdate }) {
  return <Stack gap={5}><EditorIntroduction title="Proposal foundation" description="State the client problem, then make the recommendation logic clear." /><TextArea id="problem-statement" labelText="Problem statement" helperText="Describe observed operational, commercial or risk impacts." rows={3} value={draft.problemStatement} onChange={(event) => update((current) => ({ ...current, problemStatement: event.target.value }))} /><TextArea id="solution-strategy" labelText="Recommended solution" helperText="Explain why this approach addresses the problem." rows={3} value={draft.solutionStrategy} onChange={(event) => update((current) => ({ ...current, solutionStrategy: event.target.value }))} /><ListEditor label="Solution components" values={draft.components} placeholder="e.g. Integration pilot and workflow redesign" onChange={(components) => update((current) => ({ ...current, components }))} /></Stack>
}

function Commercial({ draft, update }: { draft: ProposalDraftRequest; update: DraftUpdate }) {
  return <Stack gap={5}><EditorIntroduction title="Value and commercial logic" description="Make the outcome measurable and distinguish consultant estimates from confirmed client facts." /><StructuredEditor label="Expected business outcomes and KPIs" addLabel="Add outcome" rows={draft.businessOutcomes} empty={{ outcome: '', metric: '', target: '' }} fields={[['outcome', 'Business outcome'], ['metric', 'Metric'], ['target', 'Target']]} onChange={(businessOutcomes) => update((current) => ({ ...current, businessOutcomes }))} /><div className={styles.commercialGrid}><NumberInput id="proposal-budget" label="Estimated budget (USD)" min={0} value={draft.budget} onChange={(_, data) => update((current) => ({ ...current, budget: String(data.value) }))} /><Select id="budget-confidence" labelText="Confidence" value={draft.budgetConfidence} onChange={(event) => update((current) => ({ ...current, budgetConfidence: event.target.value }))}><SelectItem value="UNCONFIRMED" text="Unconfirmed" /><SelectItem value="LOW" text="Low" /><SelectItem value="MEDIUM" text="Medium" /><SelectItem value="HIGH" text="High" /></Select><TextInput id="budget-source" labelText="Source / basis" value={draft.budgetSource} onChange={(event) => update((current) => ({ ...current, budgetSource: event.target.value }))} /></div></Stack>
}

function Delivery({ draft, update }: { draft: ProposalDraftRequest; update: DraftUpdate }) {
  return <Stack gap={5}><EditorIntroduction title="Timeline and milestones" description="Translate the delivery window into observable milestones the client can evaluate." /><NumberInput id="timeline-weeks" label="Total timeline (weeks)" min={1} value={draft.timelineWeeks} onChange={(_, data) => update((current) => ({ ...current, timelineWeeks: Number(data.value) || 1 }))} /><StructuredEditor label="Milestones" addLabel="Add milestone" rows={draft.milestones} empty={{ phase: '', duration: '' }} fields={[['phase', 'Phase / milestone'], ['duration', 'Timing']]} onChange={(milestones) => update((current) => ({ ...current, milestones }))} /></Stack>
}

function RiskAssumptions({ draft, update, showRisks }: { draft: ProposalDraftRequest; update: DraftUpdate; showRisks: boolean }) {
  if (!showRisks) return <Stack gap={5}><EditorIntroduction title="Evidence and assumptions" description="Make the conditions behind the recommendation explicit. Attached evidence remains traceable by section." /><ListEditor label="Assumptions and dependencies" values={draft.assumptions} placeholder="e.g. Client SMEs are available for targeted validation" onChange={(assumptions) => update((current) => ({ ...current, assumptions }))} /><EvidenceSummary draft={draft} /></Stack>
  return <Stack gap={5}><EditorIntroduction title="Risks and mitigations" description="Show how delivery, operational and adoption risks will be controlled." /><StructuredEditor label="Risks" addLabel="Add risk" rows={draft.risks} empty={{ risk: '', severity: 'MEDIUM', mitigation: '' }} fields={[['risk', 'Risk'], ['severity', 'Severity'], ['mitigation', 'Mitigation']]} onChange={(risks) => update((current) => ({ ...current, risks }))} /></Stack>
}

function EditorIntroduction({ title, description }: { title: string; description: string }) {
  return <div className={styles.editorIntroduction}><p className={styles.eyebrow}>Proposal section</p><h2>{title}</h2><p>{description}</p></div>
}

function ListEditor({ label, values, placeholder, onChange }: { label: string; values: string[]; placeholder: string; onChange: (values: string[]) => void }) {
  const [page, setPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil(values.length / 2))
  const visibleValues = values.slice(page * 2, page * 2 + 2)

  useEffect(() => setPage((current) => Math.min(current, pageCount - 1)), [pageCount])

  const add = () => {
    const next = [...values, '']
    onChange(next)
    setPage(Math.floor((next.length - 1) / 2))
  }

  return <section className={styles.editorGroup}><div className={styles.editorGroupHeading}><h3>{label}</h3>{pageCount > 1 && <Pager page={page} pageCount={pageCount} onPrevious={() => setPage((current) => current - 1)} onNext={() => setPage((current) => current + 1)} />}</div><Stack gap={3}>{visibleValues.map((value, visibleIndex) => { const index = page * 2 + visibleIndex; return <div className={styles.row} key={`${label}-${index}`}><TextInput id={`${label}-${index}`} labelText="" hideLabel placeholder={placeholder} value={value} onChange={(event) => onChange(values.map((entry, position) => position === index ? event.target.value : entry))} /><Button kind="ghost" hasIconOnly iconDescription="Remove item" renderIcon={TrashCan} onClick={() => onChange(values.length === 1 ? [''] : values.filter((_, position) => position !== index))} /></div> })}<Button kind="tertiary" size="sm" renderIcon={Add} onClick={add}>Add item</Button></Stack></section>
}

function StructuredEditor<T extends Record<string, string>>({ label, addLabel, rows, empty, fields, onChange }: { label: string; addLabel: string; rows: T[]; empty: T; fields: [keyof T, string][]; onChange: (rows: T[]) => void }) {
  const [page, setPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil(rows.length / 2))
  const visibleRows = rows.slice(page * 2, page * 2 + 2)

  useEffect(() => setPage((current) => Math.min(current, pageCount - 1)), [pageCount])

  const add = () => {
    const next = [...rows, empty]
    onChange(next)
    setPage(Math.floor((next.length - 1) / 2))
  }

  return <section className={styles.editorGroup}><div className={styles.editorGroupHeading}><h3>{label}</h3>{pageCount > 1 && <Pager page={page} pageCount={pageCount} onPrevious={() => setPage((current) => current - 1)} onNext={() => setPage((current) => current + 1)} />}</div><Stack gap={3}>{visibleRows.map((row, visibleIndex) => { const index = page * 2 + visibleIndex; return <div className={styles.structuredRow} key={`${label}-${index}`}>{fields.map(([key, fieldLabel]) => key === 'severity' ? <Select key={String(key)} id={`${label}-${index}-${String(key)}`} labelText={fieldLabel} value={row[key]} onChange={(event) => onChange(rows.map((entry, position) => position === index ? { ...entry, [key]: event.target.value } : entry))}><SelectItem value="LOW" text="Low" /><SelectItem value="MEDIUM" text="Medium" /><SelectItem value="HIGH" text="High" /></Select> : <TextInput key={String(key)} id={`${label}-${index}-${String(key)}`} labelText={fieldLabel} value={row[key]} onChange={(event) => onChange(rows.map((entry, position) => position === index ? { ...entry, [key]: event.target.value } : entry))} />)}<Button kind="ghost" hasIconOnly iconDescription="Remove item" renderIcon={TrashCan} onClick={() => onChange(rows.length === 1 ? [empty] : rows.filter((_, position) => position !== index))} /></div> })}<Button kind="tertiary" size="sm" renderIcon={Add} onClick={add}>{addLabel}</Button></Stack></section>
}

function Pager({ page, pageCount, onPrevious, onNext }: { page: number; pageCount: number; onPrevious: () => void; onNext: () => void }) {
  return <div className={styles.inlinePager}><span>{page + 1}/{pageCount}</span><Button kind="ghost" size="sm" hasIconOnly iconDescription="Previous items" renderIcon={ChevronLeft} disabled={page === 0} onClick={onPrevious} /><Button kind="ghost" size="sm" hasIconOnly iconDescription="Next items" renderIcon={ChevronRight} disabled={page === pageCount - 1} onClick={onNext} /></div>
}

function EvidenceSummary({ draft }: { draft: ProposalDraftRequest }) {
  return <section className={styles.evidenceSummary}><h3>Attached sources</h3>{draft.evidenceLinks.length ? <ul>{draft.evidenceLinks.map((link) => <li key={`${link.section}-${link.sourceId}`}>{link.section}: {link.sourceId.startsWith('meeting:') ? 'Meeting discovery' : 'Research evidence'}</li>)}</ul> : <p>No sources attached yet. Return to a proposal section and select a source from the context panel.</p>}</section>
}

function ReviewPanel({ review }: { review: ProposalReview }) {
  const scores = [['Evidence grounding', review.evidenceGroundingScore], ['Client alignment', review.clientAlignmentScore], ['Commercial logic', review.commercialLogicScore]]
  const priorityIssue = review.validationIssues.find((issue) => issue.severity === 'BLOCKING') ?? review.validationIssues[0]
  return <section className={styles.reviewResult}><Tag type={review.readyToSubmit ? 'green' : 'red'}>{review.readyToSubmit ? 'Ready to submit' : 'Action required'}</Tag><h3>AI proposal review</h3>{scores.map(([label, score]) => <div className={styles.score} key={String(label)}><span>{label}</span><strong>{score}/100</strong><div><i style={{ width: `${score}%` }} /></div></div>)}<p className={styles.feedback}>{review.executiveFeedback}</p>{priorityIssue && <p className={priorityIssue.severity === 'BLOCKING' ? styles.blocking : styles.warning} key={priorityIssue.code}><WarningAlt size={16} />{priorityIssue.message}</p>}{review.improvementActions[0] && <div className={styles.reviewNextAction}><strong>Improve next</strong><span>{review.improvementActions[0]}</span></div>}</section>
}

function SaveStatus({ state }: { state: 'idle' | 'saving' | 'saved' | 'error' }) {
  if (state === 'saving') return <InlineLoading description="Saving draft" />
  if (state === 'saved') return <span className={styles.saved}>Draft saved</span>
  if (state === 'error') return <span className={styles.saveError}>Save failed</span>
  return <span className={styles.muted}>Draft changes save automatically</span>
}
