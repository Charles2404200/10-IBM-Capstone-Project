import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ProposalReview, ProposalSource } from '@/api/types'
import {
  type ProposalDraftRequest,
  useProposalChallenge,
  useProposalReview,
  useProposalWorkspace,
  useSaveProposalDraft,
  useSubmitProposal,
} from '@/api/hooks/useProposal'
import {
  attachSource,
  createEmptyProposalDraft,
  detachSource,
  proposalToDraft,
  type ProposalSection,
} from '../services/proposalDraftService'

export type DraftSaveState = 'idle' | 'saving' | 'saved' | 'error'

export function useProposalStudio(engagementId: string) {
  const workspace = useProposalWorkspace(engagementId)
  const saveDraft = useSaveProposalDraft(engagementId)
  const reviewProposal = useProposalReview(engagementId)
  const challengeProposal = useProposalChallenge(engagementId)
  const submitProposal = useSubmitProposal(engagementId)
  const [draft, setDraft] = useState<ProposalDraftRequest>(createEmptyProposalDraft)
  const [activeSection, setActiveSection] = useState<ProposalSection>('PROBLEM')
  const [review, setReview] = useState<ProposalReview | null>(null)
  const [saveState, setSaveState] = useState<DraftSaveState>('idle')
  const hydrated = useRef(false)
  const skipInitialAutosave = useRef(false)

  const proposal = workspace.data?.proposal
  const submitted = proposal?.status === 'SUBMITTED'

  useEffect(() => {
    if (!hydrated.current && workspace.data) {
      skipInitialAutosave.current = true
      setDraft(workspace.data.proposal ? proposalToDraft(workspace.data.proposal) : createEmptyProposalDraft())
      setSaveState('saved')
      hydrated.current = true
    }
  }, [workspace.data])

  const updateDraft = useCallback((updater: (current: ProposalDraftRequest) => ProposalDraftRequest) => {
    setDraft((current) => updater(current))
    setSaveState('idle')
  }, [])

  const persist = useCallback(async (): Promise<boolean> => {
    if (submitted) return true
    setSaveState('saving')
    try {
      await saveDraft.mutateAsync(draft)
      setSaveState('saved')
      return true
    } catch {
      setSaveState('error')
      return false
    }
  }, [draft, saveDraft, submitted])

  useEffect(() => {
    if (skipInitialAutosave.current) {
      skipInitialAutosave.current = false
      return
    }
    // An API rejection must not reschedule itself forever. The next learner edit
    // changes the state back to idle; explicit review/submit actions can also retry.
    if (!hydrated.current || submitted || saveState === 'saving' || saveState === 'saved' || saveState === 'error') return
    const timer = window.setTimeout(() => { void persist() }, 900)
    return () => window.clearTimeout(timer)
  }, [draft, persist, saveState, submitted])

  const attach = useCallback((source: ProposalSource) => {
    updateDraft((current) => attachSource(current, activeSection, source))
  }, [activeSection, updateDraft])

  const detach = useCallback((sourceId: string) => {
    updateDraft((current) => detachSource(current, activeSection, sourceId))
  }, [activeSection, updateDraft])

  const reviewCurrentDraft = useCallback(async () => {
    if (!await persist()) return
    reviewProposal.mutate(draft, { onSuccess: setReview })
  }, [draft, persist, reviewProposal])

  const challengeCurrentDraft = useCallback(async () => {
    if (!await persist()) return
    challengeProposal.mutate(draft)
  }, [challengeProposal, draft, persist])

  const submit = useCallback(async () => {
    if (!await persist()) return
    const result = await reviewProposal.mutateAsync(draft)
    setReview(result)
    if (result.readyToSubmit) submitProposal.mutate(draft)
  }, [draft, persist, reviewProposal, submitProposal])

  const attachedSourceIds = useMemo(() => new Set(
    draft.evidenceLinks.filter((link) => link.section === activeSection).map((link) => link.sourceId),
  ), [activeSection, draft.evidenceLinks])

  return {
    workspace, proposal, submitted, draft, activeSection, review, saveState,
    updateDraft, setActiveSection, attach, detach, attachedSourceIds,
    reviewCurrentDraft, challengeCurrentDraft, submit,
    saveDraft, reviewProposal, challengeProposal, submitProposal,
  }
}
