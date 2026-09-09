import type { LeadAuthoringRequest, LeadAuthoringView } from '@/api/types'

export type LeadAuthoringForm = Omit<LeadAuthoringRequest,
  'publicDescription' | 'potentialValueRange' | 'decisionMaker' | 'technologyStack' | 'budgetSignal' | 'painSeverity'> & {
  publicDescription: string
  potentialValueRange: string
  decisionMaker: string
  technologyStack: string
  budgetSignal: string
  painSeverity: string
}

/** Converts nullable draft API fields into controlled-input-safe authoring state. */
export function leadAuthoringFormFrom(view: LeadAuthoringView): LeadAuthoringForm {
  return {
    companyName: view.companyName,
    industry: view.industry,
    publicDescription: view.publicDescription ?? '',
    difficulty: view.difficulty,
    potentialValueRange: view.potentialValueRange ?? '',
    decisionMaker: view.decisionMaker ?? '',
    technologyStack: view.technologyStack ?? '',
    budgetSignal: view.budgetSignal ?? '',
    painSeverity: view.painSeverity ?? '',
    signals: view.signals,
  }
}
