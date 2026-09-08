import type { ProposalDraftForm } from '@/api/hooks/useProposal'

const storagePrefix = 'consulting-sim:proposal-draft:'

interface StoredProposalDraft {
  version: 1
  draft: ProposalDraftForm
}

export function loadRecoveredProposalDraft(engagementId: string): ProposalDraftForm | null {
  try {
    const raw = window.localStorage.getItem(storagePrefix + engagementId)
    if (!raw) return null
    const stored = JSON.parse(raw) as Partial<StoredProposalDraft>
    return stored.version === 1 && stored.draft ? stored.draft : null
  } catch {
    return null
  }
}

export function storeProposalDraft(engagementId: string, draft: ProposalDraftForm) {
  window.localStorage.setItem(storagePrefix + engagementId, JSON.stringify({ version: 1, draft } satisfies StoredProposalDraft))
}

export function clearRecoveredProposalDraft(engagementId: string) {
  window.localStorage.removeItem(storagePrefix + engagementId)
}
