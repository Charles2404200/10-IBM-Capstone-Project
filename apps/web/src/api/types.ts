// ─── Auth ────────────────────────────────────────────────────────────────────

export interface TokenResponse {
  accessToken: string
  userId: string
  displayName: string
  role: string
}

// ─── Scenario ────────────────────────────────────────────────────────────────

export interface PersonaSummary {
  id: string
  name: string
  jobTitle: string
  organisation: string
  communicationStyle: string
  visibleConcerns: string
}

export interface DifficultyProfile {
  informationAmbiguity: number
  stakeholderComplexity: number
  commercialPressure: number
}

export interface ScenarioBriefing {
  consultantRole: string
  objective: string
  successCriteria: string[]
  simulatedDays: number
}

export interface ScenarioSummary {
  id: string
  title: string
  industry: string
  description: string
  difficulty: number
  version: number
  status: string
  personas: PersonaSummary[]
  rubricWeights: Record<string, number>
  difficultyProfile: DifficultyProfile
  briefing: ScenarioBriefing
}

// ─── Lead ─────────────────────────────────────────────────────────────────────

export interface LeadSignal {
  id: string
  label: string
  category: string
}

export interface LeadSummary {
  id: string
  companyName: string
  industry: string
  publicDescription: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  signals: LeadSignal[]
}

// ─── Lead Intelligence (Client Profile panel) ────────────────────────────────

/** A single revealed (or not-yet-revealed) intelligence field: the value plus
 *  which evidence sequence numbers (rendered as "E-01") earned the reveal —
 *  enables the panel to explain itself ("Based on E-02, E-04"). */
export interface IntelligenceField {
  value: string | null
  supportingEvidence: number[]
}

export interface LeadIntelligence {
  leadId: string
  companyName: string
  industry: string
  evidenceCount: number
  confidenceLabel: 'LOW' | 'MEDIUM' | 'HIGH'
  confidenceScore: number
  confidenceFactors: string[]
  potentialValueRange: IntelligenceField
  decisionMaker: IntelligenceField
  technologyStack: IntelligenceField
  budgetSignal: IntelligenceField
  painSeverity: IntelligenceField
}

// ─── Research Evidence ────────────────────────────────────────────────────────

export type EvidenceType =
  | 'COMPANY_NEWS'
  | 'FINANCIAL_SIGNAL'
  | 'TECHNOLOGY_INDICATOR'
  | 'STAKEHOLDER_PROFILE'
  | 'MARKET_TREND'
  | 'HYPOTHESIS'
  | 'OTHER'

export type ConfidenceLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export type EvidenceOrigin = 'SCENARIO_CURATED' | 'AI_SYNTHESIZED' | 'USER_SUPPLIED' | 'MEETING_DISCOVERY'
export type EvidenceVerificationStatus = 'VERIFIED' | 'CORROBORATED' | 'UNVERIFIED' | 'CONTRADICTED'

export interface ResearchEvidence {
  id: string
  engagementId: string
  note: string
  hypothesis: string | null
  evidenceType: EvidenceType
  sourceUrl: string | null
  sourceTitle: string | null
  origin: EvidenceOrigin
  verificationStatus: EvidenceVerificationStatus
  occurredOn: string | null
  confidence: ConfidenceLevel
  sequenceNo: number
  supportingEvidenceIds: string[]
  createdAt: string
}

export interface SaveResearchPayload {
  note: string
  hypothesis?: string
  evidenceType: EvidenceType
  sourceUrl?: string
  sourceTitle?: string
  origin?: EvidenceOrigin
  verificationStatus?: EvidenceVerificationStatus
  occurredOn?: string
  confidence?: ConfidenceLevel
  supportingEvidenceIds?: string[]
}

export interface ResearchArtifact {
  id: string
  title: string
  sourceType: string
  summary: string
  evidenceType: EvidenceType
  confidence: ConfidenceLevel
  origin: EvidenceOrigin
  publishedOn: string
  allowedFactKeys: string[]
  correlatesWithEvidence: string[]
  relevanceRationale: string
}

/** Requirements checklist gating "Proceed to Outreach" — mirrors backend `ResearchGateStatus`. */
export interface ResearchGateStatus {
  researchCompleted: boolean
  evidenceCount: number
  requiredEvidenceCount: number
  hasStakeholderEvidence: boolean
  hasHypothesis: boolean
  confidencePercent: number
  requiredConfidencePercent: number
  ready: boolean
}

// ─── Engagement ───────────────────────────────────────────────────────────────

export type EngagementState =
  | 'QUALIFYING'
  | 'CLIENT_INTELLIGENCE'
  | 'HYPOTHESIS_READY'
  | 'OUTREACHING'
  | 'MEETING_SECURED'
  | 'PREPARING'
  | 'IN_MEETING'
  | 'DISCOVERY_COMPLETE'
  | 'PROPOSAL_DRAFT'
  | 'PROPOSAL_SUBMITTED'
  | 'CLIENT_DECISION'
  | 'REVIEW'
  | 'COMPLETED'

export interface EngagementEvent {
  id: string
  state: EngagementState
  description: string
  occurredAt: string
}

export type EngagementPhase =
  | 'LEAD'
  | 'CLIENT_INTELLIGENCE'
  | 'OUTREACH'
  | 'MEETING_PREPARATION'
  | 'LIVE_MEETING'
  | 'PROPOSAL'
  | 'OUTCOME'
  | 'REVIEW'
  | 'COMPLETED'

export interface Engagement {
  id: string
  userId: string
  scenarioId: string
  personaId: string
  state: EngagementState
  selectedLeadId: string | null
  createdAt: string
  completedAt: string | null
  events: EngagementEvent[]
  // Cockpit enrichment
  scenarioTitle: string | null
  scenarioIndustry: string | null
  leadCompanyName: string | null
  phase: EngagementPhase
  phaseLabel: string
  progressPercent: number
  nextAction: string
  evidenceCount: number
  daysElapsed: number
  meetingId: string | null
}

// ─── Outreach ─────────────────────────────────────────────────────────────────

export interface OutreachAttempt {
  id: string
  engagementId: string
  attemptNumber: number
  subject: string
  body: string
  clientReply: string | null
  outcome: 'PENDING' | 'ACCEPTED' | 'FOLLOW_UP_REQUIRED' | 'REJECTED'
  scorePersonalisation: number | null
  scoreRelevance: number | null
  scoreClarity: number | null
  scoreCallToAction: number | null
  nextAction: 'NONE' | 'SEND_FOLLOW_UP' | 'SUBMIT_CAPABILITY_BRIEF' | 'CONTINUE_TO_MEETING'
  requestTitle: string | null
  requestSummary: string | null
  requestRequirements: string[]
  createdAt: string
}

export interface CapabilityBrief {
  id: string
  engagementId: string
  relevantExperience: string
  approach: string
  caseExample: string
  clientFit: string
  clientReply: string | null
  outcome: 'PENDING' | 'ACCEPTED' | 'FOLLOW_UP_REQUIRED' | 'REJECTED'
  scoreClientFit: number | null
  scoreIndustryRelevance: number | null
  scoreEvidenceQuality: number | null
  scoreClarity: number | null
  scoreCredibility: number | null
  updatedAt: string
}

// ─── API Error (RFC 7807) ─────────────────────────────────────────────────────

export interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  violations?: Record<string, string>
}

// ─── Meeting Preparation ──────────────────────────────────────────────────────

export interface MeetingPreparation {
  id: string
  engagementId: string
  objective: string | null
  agenda: string[]
  discoveryQuestions: string[]
  readinessScore: number
  ready: boolean
}

// ─── Meeting ──────────────────────────────────────────────────────────────────

export type MeetingStatus = 'IN_PROGRESS' | 'COMPLETED'

export interface Meeting {
  id: string
  engagementId: string
  personaId: string
  status: MeetingStatus
  completedAt: string | null
  transcriptStorageReference: string | null
}

export type ConversationActor = 'LEARNER' | 'PERSONA'

export interface ConversationTurn {
  id: string
  meetingId: string
  actor: ConversationActor
  content: string
  sequence: number
  signals: string | null
  createdAt: string
}

export interface PersonaState {
  engagementId: string
  trust: number
  interest: number
  patience: number
  disclosedFacts: string[]
}

export interface MeetingTurnResult {
  learnerTurn: ConversationTurn
  personaTurn: ConversationTurn
  personaState: PersonaState
  meetingSignals: string[]
}

// ─── Proposal ─────────────────────────────────────────────────────────────────

export type ProposalDecision = 'WON' | 'LOST'

export interface Proposal {
  id: string
  engagementId: string
  problemStatement: string
  components: string[]
  budget: string
  timelineWeeks: number
  alignmentScore: number
  decision: ProposalDecision
  decisionRationale: string
  submittedAt: string
}

// ─── Assessment ───────────────────────────────────────────────────────────────

export interface CompetencyScoreView {
  name: string
  score: number
  evidenceNote: string | null
}

export interface Assessment {
  id: string
  engagementId: string
  competencyScores: CompetencyScoreView[]
  overallScore: number
  outcome: string
  feedbackSummary: string
  strengths: string[]
  improvementAreas: string[]
  generatedAt: string
}

// ─── Portfolio ────────────────────────────────────────────────────────────────

export interface CompetencyTrendPoint {
  engagementId: string
  generatedAt: string
  score: number
}

export interface CompetencyTrend {
  competencyName: string
  points: CompetencyTrendPoint[]
}

export interface CompletedEngagementView {
  engagementId: string
  scenarioId: string
  scenarioTitle: string
  industry: string
  outcome: string
  overallScore: number
  completedAt: string | null
}

export interface PortfolioSummary {
  totalEngagements: number
  completedEngagements: number
  contractsWon: number
  contractsLost: number
  averageOverallScore: number
  competencyTrends: CompetencyTrend[]
  completedEngagementsHistory: CompletedEngagementView[]
}

export interface ReplayCompetencyScore {
  competencyName: string
  score: number
  evidenceNote: string | null
}

export interface ReplayEngagementSnapshot {
  engagementId: string
  scenarioTitle: string
  personaName: string
  outcome: string
  overallScore: number
  competencyScores: ReplayCompetencyScore[]
}

export interface ReplayComparison {
  engagementA: ReplayEngagementSnapshot
  engagementB: ReplayEngagementSnapshot
}

// ─── Achievements ─────────────────────────────────────────────────────────────

export type ConditionType =
  | 'MIN_ENGAGEMENTS_COMPLETED'
  | 'MIN_ENGAGEMENTS_WON'
  | 'MIN_BEST_OVERALL_SCORE'
  | 'MIN_AVERAGE_OVERALL_SCORE'
  | 'MIN_COMPETENCY_SCORE'
  | 'MIN_DISTINCT_SCENARIOS_COMPLETED'
  | 'MIN_WIN_RATE_PERCENT'

export type LogicalOperator = 'AND' | 'OR'

/** Mirrors the backend's flat ConditionNode DTO: either a GROUP (operator + children)
 *  or a LEAF (type + threshold, + competencyName for MIN_COMPETENCY_SCORE). */
export interface ConditionNode {
  kind: 'GROUP' | 'LEAF'
  operator: LogicalOperator | null
  children: ConditionNode[] | null
  type: ConditionType | null
  competencyName: string | null
  threshold: number | null
}

export interface AchievementSummary {
  id: string
  name: string
  description: string
  iconKey: string
  unlocked: boolean
  unlockedAt: string | null
  progressPercent: number
}

export interface AchievementAdminView {
  id: string
  name: string
  description: string
  iconKey: string
  active: boolean
  rule: ConditionNode
}

export interface UpsertAchievementRequest {
  name: string
  description: string
  iconKey: string
  rule: ConditionNode
}

// ─── Admin: Scenario Authoring ────────────────────────────────────────────────

export interface CreateScenarioRequest {
  title: string
  industry: string
  description: string
  difficulty: number
}

export interface CreatePersonaRequest {
  name: string
  jobTitle: string
  organisation: string
  communicationStyle: string
  visibleConcerns: string
  hiddenConcerns: string
  businessGoals: string
}

export interface KnowledgeDocumentUploadRequest {
  personaId: string | null
  collection: 'SCENARIO_TRUTH' | 'CONSULTING_PRACTICE' | 'ASSESSMENT_RUBRIC'
  title: string
  content: string
}

// ─── Admin: User Management ───────────────────────────────────────────────────

export interface UserSummary {
  id: string
  email: string
  displayName: string
  role: string
  active: boolean
}

