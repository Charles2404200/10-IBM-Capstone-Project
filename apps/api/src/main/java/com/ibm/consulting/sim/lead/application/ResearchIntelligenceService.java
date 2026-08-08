package com.ibm.consulting.sim.lead.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ResearchIntelligenceService {

    private final EngagementRepository engagementRepository;
    private final LeadRepository leadRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;

    public ResearchIntelligenceService(EngagementRepository engagementRepository,
                                       LeadRepository leadRepository,
                                       ResearchEvidenceRepository evidenceRepository,
                                       AiOrchestrationService aiOrchestrationService,
                                       ObjectMapper objectMapper) {
        this.engagementRepository = engagementRepository;
        this.leadRepository = leadRepository;
        this.evidenceRepository = evidenceRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ResearchArtifactResponse> generate(UUID engagementId, UUID userId, EvidenceType type) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        Lead lead = loadLead(engagement);
        Map<String, String> facts = canonicalFacts(lead);
        List<ResearchEvidence> discovered = evidenceRepository.findByEngagementId(engagementId);
        ClientIntelligenceResponseParser parser = new ClientIntelligenceResponseParser(objectMapper, facts, type);
        List<ResearchArtifactResponse> artifacts = aiOrchestrationService.execute(
                "client_intelligence",
                engagementId,
                buildPrompt(lead, engagement, type, facts, discovered, null),
                1,
                parser,
                () -> templateGenerate(lead, type));
        return removeDuplicates(artifacts, discovered);
    }

    private List<ResearchArtifactResponse> templateGenerate(Lead lead, EvidenceType type) {
        return switch (type) {
            case COMPANY_NEWS -> companyNews(lead);
            case STAKEHOLDER_PROFILE -> stakeholderProfiles(lead);
            case FINANCIAL_SIGNAL -> financialSignals(lead);
            case TECHNOLOGY_INDICATOR -> technologySignals(lead);
            case MARKET_TREND -> marketTrends(lead);
            case OTHER, HYPOTHESIS -> List.of();
        };
    }

    private List<ResearchArtifactResponse> removeDuplicates(List<ResearchArtifactResponse> artifacts,
                                                            List<ResearchEvidence> existingEvidence) {
        java.util.Set<String> existingFingerprints = existingEvidence.stream()
                .map(e -> fingerprint(e.getSourceTitle() + " " + e.getNote()))
                .collect(java.util.stream.Collectors.toSet());
        return artifacts.stream()
                .filter(a -> !existingFingerprints.contains(fingerprint(a.title() + " " + a.summary())))
                .toList();
    }

    private String fingerprint(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Transactional(readOnly = true)
    public ResearchArtifactResponse analyzeUserContext(UUID engagementId, UUID userId, String context) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        Lead lead = loadLead(engagement);
        List<ResearchEvidence> evidence = evidenceRepository.findByEngagementId(engagementId);
        EvidenceType inferredType = inferType(context);
        List<String> relatedEvidence = evidence.stream()
                .filter(e -> e.getEvidenceType() == inferredType)
                .map(e -> "E-%02d".formatted(e.getSequenceNo()))
                .toList();

        String summary = "User-supplied intelligence: %s".formatted(context.trim());
        String rationale = relatedEvidence.isEmpty()
                ? "No scenario evidence currently corroborates this input; treat it as unverified."
                : "This appears related to existing %s evidence and should be validated before it informs the hypothesis."
                        .formatted(inferredType.name().toLowerCase(Locale.ROOT).replace('_', ' '));

        return new ResearchArtifactResponse(
                "user-context-" + Integer.toHexString(context.hashCode()),
                "External Intelligence Review",
                "User-supplied context",
                summary,
                inferredType.name(),
                ConfidenceLevel.LOW.name(),
                EvidenceOrigin.USER_SUPPLIED.name(),
                LocalDate.now(),
                List.of("user_supplied_unverified"),
                relatedEvidence,
                "%s Canonical truth for %s is not overwritten by this input."
                        .formatted(rationale, lead.getCompanyName()));
    }

    private Engagement loadOwnedEngagement(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        return engagement;
    }

    private Lead loadLead(Engagement engagement) {
        if (engagement.getSelectedLeadId() == null) {
            throw new IllegalStateException("No lead selected for engagement");
        }
        return leadRepository.findById(engagement.getSelectedLeadId())
                .orElseThrow(() -> new NotFoundException("Lead", engagement.getSelectedLeadId()));
    }

    private Map<String, String> canonicalFacts(Lead lead) {
        Map<String, String> facts = new java.util.LinkedHashMap<>();
        putFact(facts, "company_name", lead.getCompanyName());
        putFact(facts, "industry", lead.getIndustry());
        putFact(facts, "public_description", lead.getPublicDescription());
        putFact(facts, "decision_maker", lead.getDecisionMaker());
        putFact(facts, "technology_stack", lead.getTechnologyStack());
        putFact(facts, "budget_signal", lead.getBudgetSignal());
        putFact(facts, "pain_severity", lead.getPainSeverity());
        putFact(facts, "potential_value_range", lead.getPotentialValueRange());
        lead.getSignals().forEach(signal -> putFact(facts, "signal_" + signal.getCategory().toLowerCase(Locale.ROOT),
                signal.getLabel()));
        return Map.copyOf(facts);
    }

    private void putFact(Map<String, String> facts, String key, String value) {
        if (value != null && !value.isBlank()) {
            facts.put(key, value);
        }
    }

    private String buildPrompt(Lead lead, Engagement engagement, EvidenceType type, Map<String, String> facts,
                               List<ResearchEvidence> discovered, String userContext) {
        String factLines = facts.entrySet().stream()
                .map(e -> "- %s: %s".formatted(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.joining("\n"));
        String discoveredLines = discovered.stream()
                .map(e -> "- E-%02d [%s/%s]: %s".formatted(
                        e.getSequenceNo(), e.getEvidenceType(), e.getOrigin(), e.getNote()))
                .collect(java.util.stream.Collectors.joining("\n"));

        return """
                You are the Client Intelligence Engine for an enterprise consulting training simulation.
                Generate realistic simulated research artifacts for the learner to review.

                HARD RULES:
                - You may ONLY use the canonical facts listed below.
                - Do not invent companies, budgets, people, dates, technologies, risks, facts, URLs, or outcomes.
                - Every artifact MUST cite at least one supportedFactIds value from the canonical facts.
                - Learner context, if present, is unverified and must not overwrite canonical facts.
                - Return ONLY JSON with an "artifacts" array. No markdown.

                Required JSON schema:
                {
                  "artifacts": [
                    {
                      "id": "short-stable-id",
                      "title": "...",
                      "category": "%s",
                      "content": "...",
                      "sourceType": "SIMULATED_REPORT|COMPANY_NEWS|STAKEHOLDER_PROFILE|FINANCIAL_REPORT|TECHNOLOGY_NOTE|MARKET_BRIEF",
                      "reliability": "LOW|MEDIUM|HIGH",
                      "supportedFactIds": ["fact-id-from-list"],
                      "relevance": 0.0,
                      "confidence": 0.0
                    }
                  ]
                }

                Engagement state: %s
                Research category: %s
                Scenario difficulty: %s
                Company: %s

                Canonical facts:
                %s

                Learner already discovered:
                %s

                Optional learner context:
                %s
                """.formatted(
                type.name(),
                engagement.getState().name(),
                type.name(),
                lead.getDifficulty().name(),
                lead.getCompanyName(),
                factLines,
                discoveredLines.isBlank() ? "None" : discoveredLines,
                userContext == null || userContext.isBlank() ? "None" : userContext);
    }

    private List<ResearchArtifactResponse> companyNews(Lead lead) {
        List<ResearchArtifactResponse> artifacts = new ArrayList<>();
        artifacts.add(artifact("company-news-1", "Digital transformation programme expands at " + lead.getCompanyName(),
                "Company news", "%s is increasing focus on operational modernisation. Visible signals suggest %s."
                        .formatted(lead.getCompanyName(), lead.getPainSeverity() != null ? lead.getPainSeverity() : "meaningful business pressure"),
                EvidenceType.COMPANY_NEWS, ConfidenceLevel.HIGH, "pain_severity"));
        artifacts.add(artifact("company-news-2", "Leadership team faces execution pressure",
                "Industry press", "Recent public signals indicate leadership attention on delivery risk and measurable outcomes.",
                EvidenceType.COMPANY_NEWS, ConfidenceLevel.MEDIUM, "commercial_pressure"));
        return artifacts;
    }

    private List<ResearchArtifactResponse> stakeholderProfiles(Lead lead) {
        return List.of(
                artifact("stakeholder-1", lead.getDecisionMaker() != null ? lead.getDecisionMaker() : "Potential executive sponsor",
                        "Stakeholder profile",
                        "%s appears to be the most relevant stakeholder to validate pain, sponsorship and decision process."
                                .formatted(lead.getDecisionMaker() != null ? lead.getDecisionMaker() : "A senior operational leader"),
                        EvidenceType.STAKEHOLDER_PROFILE, ConfidenceLevel.HIGH, "decision_maker"),
                artifact("stakeholder-2", "Commercial stakeholder influence",
                        "Stakeholder map",
                        "Budget and risk approval likely require commercial validation beyond the primary business sponsor.",
                        EvidenceType.STAKEHOLDER_PROFILE, ConfidenceLevel.MEDIUM, "stakeholder_complexity"));
    }

    private List<ResearchArtifactResponse> financialSignals(Lead lead) {
        return List.of(
                artifact("financial-1", "Funding signal under review",
                        "Financial intelligence",
                        "%s. Any proposal should connect spend to measurable operational or risk reduction outcomes."
                                .formatted(lead.getBudgetSignal() != null ? lead.getBudgetSignal() : "Budget is not yet confirmed"),
                        EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, "budget_signal"),
                artifact("financial-2", "Potential opportunity sizing",
                        "Commercial analysis",
                        "The likely opportunity range is %s, but this should be validated through discovery before proposal."
                                .formatted(lead.getPotentialValueRange() != null ? lead.getPotentialValueRange() : "not yet confirmed"),
                        EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, "potential_value_range"));
    }

    private List<ResearchArtifactResponse> technologySignals(Lead lead) {
        return List.of(
                artifact("technology-1", "Current technology environment",
                        "Technology research",
                        "%s. This may create integration, change-management and rollout risk."
                                .formatted(lead.getTechnologyStack() != null ? lead.getTechnologyStack() : "Technology stack is not yet confirmed"),
                        EvidenceType.TECHNOLOGY_INDICATOR, ConfidenceLevel.HIGH, "technology_stack"),
                artifact("technology-2", "Implementation risk indicator",
                        "Architecture note",
                        "Legacy environments suggest phased migration, rollback planning and stakeholder training should be explored.",
                        EvidenceType.TECHNOLOGY_INDICATOR, ConfidenceLevel.MEDIUM, "implementation_risk"));
    }

    private List<ResearchArtifactResponse> marketTrends(Lead lead) {
        return List.of(artifact("market-1", lead.getIndustry() + " modernisation pressure",
                "Market trend", "Comparable organisations are investing in interoperability, analytics and process visibility.",
                EvidenceType.MARKET_TREND, ConfidenceLevel.MEDIUM, "industry_trend"));
    }

    private ResearchArtifactResponse artifact(String id, String title, String sourceType, String summary,
                                              EvidenceType type, ConfidenceLevel confidence, String factKey) {
        return new ResearchArtifactResponse(id, title, sourceType, summary, type.name(), confidence.name(),
                EvidenceOrigin.AI_SYNTHESIZED.name(), LocalDate.now().minusDays(14), List.of(factKey), List.of(),
                "Generated from scenario-approved facts only; learner must decide whether it is relevant.");
    }

    private EvidenceType inferType(String context) {
        String c = context.toLowerCase(Locale.ROOT);
        if (c.contains("budget") || c.contains("fund") || c.contains("$") || c.contains("cost")) return EvidenceType.FINANCIAL_SIGNAL;
        if (c.contains("cloud") || c.contains("system") || c.contains("platform") || c.contains("integration")) return EvidenceType.TECHNOLOGY_INDICATOR;
        if (c.contains("cio") || c.contains("cfo") || c.contains("vp") || c.contains("stakeholder")) return EvidenceType.STAKEHOLDER_PROFILE;
        if (c.contains("market") || c.contains("industry")) return EvidenceType.MARKET_TREND;
        return EvidenceType.COMPANY_NEWS;
    }
}
