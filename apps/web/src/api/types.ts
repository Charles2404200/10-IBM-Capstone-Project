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

export type UserRole = 'LEARNER' | 'SCENARIO_AUTHOR' | 'REVIEWER' | 'ADMINISTRATOR'

export interface AdminUserSummary {
  id: string
  email: string
  displayName: string
  role: UserRole
  active: boolean
}

export interface AiProviderStat {
  providerId: string
  available: boolean
  circuitState: string
  requestsToday: number
  successCount: number
  failureCount: number
  avgLatencyMs: number
  fallbackRatePercent: number
  quotaUsed: number
  quotaLimit: number
}

export interface AiOperationsResponse {
  mockMode: boolean
  providers: AiProviderStat[]
  routing: Record<string, string[]>
  parallelEnabled: boolean
  parallelMaxCandidates: number
}

export interface ScenarioActivity {
  scenarioId: string
  title: string
  engagementCount: number
  completedCount: number
  averageAssessmentScore: number | null
}

export interface PlatformOverview {
  totalEngagements: number
  activeEngagements: number
  completedEngagements: number
  completionRatePercent: number
  averageAssessmentScore: number | null
  engagementsByState: Record<string, number>
  scenarios: ScenarioActivity[]
}

export interface GameplayDifficultyProfile {
  level: 'EASY' | 'MEDIUM' | 'HARD'
  researchArtifactsPerAction: number
  distractorArtifactsPerAction: number
  contradictionCount: number
  initialTrust: number
  initialInterest: number
  initialPatience: number
  meetingTurnLimit: number
  budgetVisible: boolean
  timelinePressureDays: number
  requiredEvidenceCount: number
  requiredConfidencePercent: number
  outreachAcceptanceThreshold: number
  proposalEvidenceCoverageThreshold: number
  personaResistance: number
  scoringTolerance: number
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
  gameplayDifficulty?: GameplayDifficultyProfile
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
  | 'MEETING_FAILED'
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
  | 'MEETING_REVIEW'
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
  completionOutcome: 'PASSED' | 'FAILED' | null
  debriefFeedback: string | null
  debriefTips: string[]
  terminationReason: 'UNPROFESSIONAL_CONDUCT' | 'RELATIONSHIP_THRESHOLD_BREACH' | null
  terminationMessage: string | null
  meetingRetryAvailable: boolean
  meetingRetriesRemaining: number
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
  termination: MeetingTermination | null
  responseOptions: MeetingResponseOptions | null
}

export interface MeetingTermination {
  reason: 'UNPROFESSIONAL_CONDUCT' | 'RELATIONSHIP_THRESHOLD_BREACH'
  message: string
  retryGuidance: string[]
  meetingRetryAvailable: boolean
  meetingRetriesRemaining: number
}

export type MeetingInteractionMode = 'GUIDED' | 'FREEFORM'

export interface MeetingResponseOptions {
  interactionMode: MeetingInteractionMode
  sourceSequence: number
  options: string[]
  available: boolean
  unavailableReason: string | null
}

// ─── Proposal ─────────────────────────────────────────────────────────────────

export type ProposalDecision = 'PENDING' | 'WON' | 'LOST'
export type ProposalStatus = 'DRAFT' | 'SUBMITTED'
export type ClientDecisionOutcome =
  | 'PILOT_APPROVED'
  | 'PROPOSAL_ACCEPTED'
  | 'REVISION_REQUESTED'
  | 'FURTHER_DISCOVERY_REQUIRED'
  | 'DEFERRED'
  | 'REJECTED'
  | 'STRATEGIC_PARTNERSHIP'

export interface ProposalBusinessOutcome {
  outcome: string
  metric: string
  target: string
}

export interface ProposalMilestone {
  phase: string
  duration: string
}

export interface ProposalRisk {
  risk: string
  severity: string
  mitigation: string
}

export interface ProposalEvidenceLink {
  section: string
  sourceId: string
}

export interface ProposalSource {
  id: string
  label: string
  type: 'RESEARCH_EVIDENCE' | 'MEETING_DISCOVERY'
  content: string
  reliability: string
}

export interface ProposalWorkspace {
  proposal: Proposal | null
  sources: ProposalSource[]
}

export interface ProposalValidationIssue {
  severity: 'BLOCKING' | 'WARNING'
  code: string
  message: string
  section: string
}

export interface ClientAlignmentItem {
  sourceId: string
  clientPriority: string
  coverage: 'STRONG' | 'PARTIAL' | 'GAP'
  detail: string
}

export interface ProposalReview {
  readyToSubmit: boolean
  validationIssues: ProposalValidationIssue[]
  clientAlignment: ClientAlignmentItem[]
  problemDefinitionScore: number
  evidenceGroundingScore: number
  clientAlignmentScore: number
  commercialLogicScore: number
  riskCoverageScore: number
  feasibilityScore: number
  executiveFeedback: string
  improvementActions: string[]
}

export interface ProposalChallenge {
  concerns: string[]
}

export interface ProposalDecisionDimension {
  dimension: string
  score: number
  interpretation: string
}

export interface ProposalDecisionInsight {
  category: 'STRENGTH' | 'CONCERN' | 'CONDITION'
  detail: string
}

export interface ProposalEvidenceImpact {
  claim: string
  supportLevel: 'WELL_SUPPORTED' | 'PARTIALLY_SUPPORTED' | 'UNSUPPORTED'
  explanation: string
}

export interface ProposalDecisionExplanation {
  message: string
}

export interface Proposal {
  id: string
  engagementId: string
  status: ProposalStatus
  problemStatement: string
  solutionStrategy: string | null
  components: string[]
  budget: string
  timelineWeeks: number
  budgetConfidence: string | null
  budgetSource: string | null
  businessOutcomes: ProposalBusinessOutcome[]
  milestones: ProposalMilestone[]
  risks: ProposalRisk[]
  assumptions: string[]
  evidenceLinks: ProposalEvidenceLink[]
  alignmentScore: number
  decision: ProposalDecision
  decisionRationale: string
  clientResponse: string | null
  clientDecisionOutcome: ClientDecisionOutcome
  decisionConfidence: number
  learnerPerformanceScore: number
  decisionDimensions: ProposalDecisionDimension[]
  decisionInsights: ProposalDecisionInsight[]
  evidenceImpacts: ProposalEvidenceImpact[]
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

