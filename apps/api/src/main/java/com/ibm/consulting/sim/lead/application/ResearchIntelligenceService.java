package com.ibm.consulting.sim.lead.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.CanonicalFact;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ResearchSource;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlock;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlockType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class ResearchIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ResearchIntelligenceService.class);

    private final EngagementRepository engagementRepository;
    private final LeadRepository leadRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    /** L1 cache keeps the document deck interactive even if a distributed cache is degraded. */
    private final Cache<String, List<ResearchArtifactResponse>> sourceDeckCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
    private final Cache<String, ResearchSourceDeckResponse> completeDeckCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
    private final DifficultyProfileService difficultyProfileService;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioAuthoringConfigService authoringConfigService;
    private final ExecutorService researchSourceDeckExecutor;

    public ResearchIntelligenceService(EngagementRepository engagementRepository,
                                       LeadRepository leadRepository,
                                       ResearchEvidenceRepository evidenceRepository,
                                       AiOrchestrationService aiOrchestrationService,
                                       ObjectMapper objectMapper,
                                       DifficultyProfileService difficultyProfileService,
                                       ScenarioRepository scenarioRepository,
                                       ScenarioAuthoringConfigService authoringConfigService,
                                       @Qualifier("researchSourceDeckExecutor") ExecutorService researchSourceDeckExecutor) {
        this.engagementRepository = engagementRepository;
        this.leadRepository = leadRepository;
        this.evidenceRepository = evidenceRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.difficultyProfileService = difficultyProfileService;
        this.scenarioRepository = scenarioRepository;
        this.authoringConfigService = authoringConfigService;
        this.researchSourceDeckExecutor = researchSourceDeckExecutor;
    }

    /** Opens all learner-facing document lanes concurrently and caches the assembled deck. */
    @Transactional(readOnly = true)
    public ResearchSourceDeckResponse generateDeck(UUID engagementId, UUID userId) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        Lead lead = loadLead(engagement);
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        Scenario scenario = loadScenario(engagement);
        List<ResearchEvidence> discovered = evidenceRepository.findByEngagementId(engagementId);
        String deckKey = "deck:" + cacheKey(lead, scenario, EvidenceType.COMPANY_NEWS, discovered, profile);
        ResearchSourceDeckResponse cached = completeDeckCache.getIfPresent(deckKey);
        if (cached != null) {
            return cached;
        }

        List<EvidenceType> lanes = List.of(
                EvidenceType.COMPANY_NEWS,
                EvidenceType.STAKEHOLDER_PROFILE,
                EvidenceType.FINANCIAL_SIGNAL,
                EvidenceType.TECHNOLOGY_INDICATOR);
        Map<EvidenceType, CompletableFuture<List<ResearchArtifactResponse>>> futures = new java.util.LinkedHashMap<>();
        for (EvidenceType lane : lanes) {
            futures.put(lane, CompletableFuture.supplyAsync(() -> generate(engagementId, userId, lane), researchSourceDeckExecutor));
        }
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        Map<String, List<ResearchArtifactResponse>> sourcesByType = new java.util.LinkedHashMap<>();
        futures.forEach((lane, future) -> sourcesByType.put(lane.name(), future.join()));
        ResearchSourceDeckResponse response = new ResearchSourceDeckResponse(sourcesByType);
        completeDeckCache.put(deckKey, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ResearchArtifactResponse> generate(UUID engagementId, UUID userId, EvidenceType type) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        Lead lead = loadLead(engagement);
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        Scenario scenario = loadScenario(engagement);
        ScenarioAuthoringConfig authoringConfig = authoringConfigService.forScenario(scenario);
        List<ResearchEvidence> discovered = evidenceRepository.findByEngagementId(engagementId);
        String cacheKey = cacheKey(lead, scenario, type, discovered, profile);
        List<ResearchArtifactResponse> cached = cachedArtifacts(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<ResearchArtifactResponse> sources = authoringConfig.researchSources().stream()
                .filter(source -> source.evidenceType() == type)
                .map(this::toArtifact)
                .toList();
        if (sources.isEmpty()) {
            // The document surface is AI-authored from the scenario's canonical
            // facts. The deterministic pack is an availability fallback only.
            sources = synthesizeSourceDeck(engagementId, engagement, lead, scenario, type, profile, authoringConfig, discovered);
        } else {
            // Authored source packs stay canonical, while difficulty still supplies
            // bounded low-reliability context that learners must assess critically.
            sources = shapeForDifficulty(lead, type, sources, profile);
        }
        List<ResearchArtifactResponse> filtered = removeDuplicates(sources, discovered);
        cacheArtifacts(cacheKey, filtered);
        return filtered;
    }

    private List<ResearchArtifactResponse> synthesizeSourceDeck(UUID engagementId, Engagement engagement, Lead lead,
                                                                  Scenario scenario, EvidenceType type,
                                                                  DifficultyProfile profile,
                                                                  ScenarioAuthoringConfig authoringConfig,
                                                                  List<ResearchEvidence> discovered) {
        Map<String, String> facts = canonicalFacts(lead, profile.budgetVisible(), authoringConfig, scenario, type);
        return aiOrchestrationService.execute(
                "client_intelligence",
                engagementId,
                buildSourceDeckPrompt(engagement, lead, scenario, type, profile, facts, discovered),
                3,
                new ClientIntelligenceResponseParser(objectMapper, facts, type),
                () -> templateGenerate(lead, scenario, type, profile, authoringConfig));
    }

    @SuppressWarnings("unchecked")
    private List<ResearchArtifactResponse> cachedArtifacts(String key) {
        return sourceDeckCache.getIfPresent(key);
    }

    private void cacheArtifacts(String key, List<ResearchArtifactResponse> artifacts) {
        sourceDeckCache.put(key, List.copyOf(artifacts));
    }

    private String cacheKey(Lead lead, Scenario scenario, EvidenceType type, List<ResearchEvidence> discovered, DifficultyProfile profile) {
        String discoveredFingerprint = discovered.stream()
                .map(e -> e.getId() + ":" + e.getEvidenceType() + ":" + e.getSequenceNo())
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
        String problemFrame = String.join("|", scenario.getBusinessSituation(), scenario.getObservableSymptom(),
                scenario.getConsultingMandate(), String.join("|", scenario.getUnknownsToValidate()));
        return "%s:%s:%s:%s:%s".formatted(
                lead.getId(), profile.hashCode(), type.name(), Integer.toHexString(problemFrame.hashCode()),
                Integer.toHexString(discoveredFingerprint.hashCode()));
    }

    private List<ResearchArtifactResponse> templateGenerate(Lead lead, Scenario scenario, EvidenceType type, DifficultyProfile profile,
                                                            ScenarioAuthoringConfig authoringConfig) {
        List<ResearchArtifactResponse> base = switch (type) {
            case COMPANY_NEWS -> companyNews(lead, scenario);
            case STAKEHOLDER_PROFILE -> stakeholderProfiles(lead, scenario);
            case FINANCIAL_SIGNAL -> financialSignals(lead, scenario, profile.budgetVisible());
            case TECHNOLOGY_INDICATOR -> technologySignals(lead, scenario);
            case MARKET_TREND -> marketTrends(lead);
            case OTHER, HYPOTHESIS -> List.of();
        };
        List<ResearchArtifactResponse> withAuthoredFacts = new ArrayList<>(base);
        authoringConfig.canonicalFacts().stream()
                .filter(CanonicalFact::availableInResearch)
                .filter(fact -> fact.evidenceType() == type)
                .forEach(fact -> withAuthoredFacts.add(artifact("author-" + fact.id(), fact.label(), "Scenario-approved source",
                        fact.value(), type, ConfidenceLevel.HIGH, fact.id())));
        return shapeForDifficulty(lead, type, withAuthoredFacts, profile);
    }

    private ResearchArtifactResponse toArtifact(ResearchSource source) {
        return new ResearchArtifactResponse("source-" + source.id(), source.title(), source.sourceType(), source.summary(),
                source.evidenceType().name(), source.confidence().name(), EvidenceOrigin.SCENARIO_CURATED.name(),
                LocalDate.now().minusDays(14), source.relevanceScore(), List.of("source-" + source.id()), List.of(),
                "Assess this source against the client problem before using it in your case.", source.effectiveBlocks());
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
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        Scenario scenario = loadScenario(engagement);
        ScenarioAuthoringConfig authoringConfig = authoringConfigService.forScenario(scenario);
        Map<String, String> facts = canonicalFacts(lead, profile.budgetVisible(), authoringConfig, scenario, inferredType);
        List<String> relatedEvidence = evidence.stream()
                .filter(e -> e.getEvidenceType() == inferredType)
                .map(e -> "E-%02d".formatted(e.getSequenceNo()))
                .toList();

        ResearchArtifactResponse aiCorrelation = aiOrchestrationService.execute(
                "client_intelligence",
                engagementId,
                buildPrompt(lead, engagement, scenario, inferredType, facts, evidence, context, profile),
                2,
                new ClientIntelligenceResponseParser(objectMapper, facts, inferredType),
                () -> List.of(externalContextFallback(context, inferredType))).stream().findFirst()
                .orElseGet(() -> externalContextFallback(context, inferredType));

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
                35,
                List.of("user_supplied_unverified"),
                relatedEvidence,
                "%s AI correlation reviewed approved scenario facts (%s). Canonical truth for %s is not overwritten by this input."
                        .formatted(rationale, String.join(", ", aiCorrelation.allowedFactKeys()), lead.getCompanyName()));
    }

    private ResearchArtifactResponse externalContextFallback(String context, EvidenceType type) {
        return new ResearchArtifactResponse("external-context", "External Intelligence Review", "User-supplied context",
                context.trim(), type.name(), ConfidenceLevel.LOW.name(), EvidenceOrigin.USER_SUPPLIED.name(),
                LocalDate.now(), 35, List.of(), List.of(), "Unverified learner context.");
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

    private Scenario loadScenario(Engagement engagement) {
        return scenarioRepository.findById(engagement.getScenarioId())
                .orElseThrow(() -> new NotFoundException("Scenario", engagement.getScenarioId()));
    }

    private Map<String, String> canonicalFacts(Lead lead, boolean budgetVisible, ScenarioAuthoringConfig authoringConfig,
                                               Scenario scenario, EvidenceType researchType) {
        Map<String, String> facts = new java.util.LinkedHashMap<>();
        putFact(facts, "company_name", lead.getCompanyName());
        putFact(facts, "industry", lead.getIndustry());
        putFact(facts, "public_description", lead.getPublicDescription());
        putFact(facts, "decision_maker", lead.getDecisionMaker());
        putFact(facts, "technology_stack", lead.getTechnologyStack());
        if (budgetVisible) putFact(facts, "budget_signal", lead.getBudgetSignal());
        putFact(facts, "pain_severity", lead.getPainSeverity());
        putFact(facts, "potential_value_range", lead.getPotentialValueRange());
        lead.getSignals().forEach(signal -> putFact(facts, "signal_" + signal.getCategory().toLowerCase(Locale.ROOT),
                signal.getLabel()));
        putFact(facts, "business_situation", scenario.getBusinessSituation());
        putFact(facts, "observable_symptom", scenario.getObservableSymptom());
        putFact(facts, "consulting_mandate", scenario.getConsultingMandate());
        authoringConfig.canonicalFacts().stream()
                .filter(CanonicalFact::availableInResearch)
                .filter(fact -> fact.evidenceType() == researchType)
                .forEach(fact -> putFact(facts, fact.id(), fact.value()));
        return Map.copyOf(facts);
    }

    private void putFact(Map<String, String> facts, String key, String value) {
        if (value != null && !value.isBlank()) {
            facts.put(key, value);
        }
    }

    private String buildPrompt(Lead lead, Engagement engagement, Scenario scenario, EvidenceType type, Map<String, String> facts,
                               List<ResearchEvidence> discovered, String userContext, DifficultyProfile profile) {
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
                Gameplay difficulty: %s. Return up to %d artifacts: no more than %d may be distractors or ambiguous context.
                Sensitive budget visibility: %s. Contradiction pressure: %d. Do not disclose budget details when visibility is false.
                Company: %s

                Scenario problem frame (use this only to prioritize what is relevant; it is not a diagnosis or a solution):
                - Business situation: %s
                - Observable symptom: %s
                - Consulting mandate: %s
                - Unknowns to validate: %s

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
                profile.level().name(), profile.researchArtifactsPerAction(), profile.distractorArtifactsPerAction(),
                profile.budgetVisible(), profile.contradictionCount(),
                lead.getCompanyName(),
                scenario.getBusinessSituation(), scenario.getObservableSymptom(), scenario.getConsultingMandate(),
                scenario.getUnknownsToValidate().isEmpty() ? "None specified" : String.join("; ", scenario.getUnknownsToValidate()),
                factLines,
                discoveredLines.isBlank() ? "None" : discoveredLines,
                userContext == null || userContext.isBlank() ? "None" : userContext);
    }

    /**
     * Creates the content for the immersive source reader. Presentation is
     * deliberately handled by React templates; this prompt produces research
     * substance only, anchored to the exact scenario facts visible to the lane.
     */
    private String buildSourceDeckPrompt(Engagement engagement, Lead lead, Scenario scenario, EvidenceType type,
                                         DifficultyProfile profile, Map<String, String> facts,
                                         List<ResearchEvidence> discovered) {
        String factLines = facts.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
        String discoveredLines = discovered.stream()
                .map(evidence -> "- E-%02d [%s]: %s".formatted(
                        evidence.getSequenceNo(), evidence.getEvidenceType(), evidence.getNote()))
                .collect(java.util.stream.Collectors.joining("\n"));

        return """
                You write simulated, enterprise research documents for a consulting training product.
                Produce TWO substantial source documents for the requested research lane. The learner will read,
                highlight, and assess them before forming a hypothesis, so every document must contain useful,
                client-specific signals rather than generic consulting advice.

                NON-NEGOTIABLE GROUNDING RULES:
                - Use ONLY the canonical facts provided below. Never invent names, companies, figures, budgets,
                  dates, systems, causes, outcomes, URLs, stakeholders, or quotations.
                - A source may explain implications and open questions, but must clearly distinguish them from facts.
                - Every artifact must cite the exact canonical fact keys it uses in supportedFactIds.
                - Do not restate the whole scenario description. Select and connect the facts that matter for this lane.
                - A "QUOTE" block may only repeat a canonical fact word-for-word and must have no invented attribution.
                - Include one low-confidence or ambiguous context source only when the difficulty requires a distractor.
                - Return JSON only. No markdown and no prose outside the JSON.

                Required JSON schema:
                {
                  "artifacts": [
                    {
                      "id": "short-stable-id",
                      "title": "specific, reader-facing title",
                      "category": "%s",
                      "content": "a concise, factual deck preview",
                      "sourceType": "COMPANY_NEWS|STAKEHOLDER_PROFILE|FINANCIAL_REPORT|TECHNOLOGY_NOTE|MARKET_BRIEF|SIMULATED_REPORT",
                      "reliability": "LOW|MEDIUM|HIGH",
                      "supportedFactIds": ["exact-fact-key"],
                      "relevance": 0.0,
                      "confidence": 0.0,
                      "blocks": [
                        {"type": "PARAGRAPH", "content": "..."},
                        {"type": "METRIC", "content": "..."},
                        {"type": "CAPTION", "content": "..."}
                      ]
                    }
                  ]
                }

                Document requirements:
                - Return exactly 2 artifacts, each with 6 to 9 blocks and at least 450 characters across its blocks.
                - COMPANY_NEWS: write an industry-news analysis with a headline, a client-specific operating signal,
                  likely business implication, and an explicitly open question.
                - STAKEHOLDER_PROFILE: write a credible dossier that separates known role/context from priorities,
                  decision influence, and questions to validate.
                - FINANCIAL_SIGNAL: write an analyst or internal finance brief that separates confirmed commercial
                  signals from assumptions, baseline questions, and approval uncertainty.
                - TECHNOLOGY_INDICATOR: write a technical briefing that explains known systems, possible operational
                  implications, dependencies to validate, and the appropriate discovery focus.
                - Use METRIC only for a numeric fact present in the canonical facts; otherwise use PARAGRAPH or CAPTION.
                - Add exactly one CAPTION per artifact that tells the learner what must be corroborated before this
                  source can support a grounded hypothesis.

                Engagement state: %s
                Research lane: %s
                Difficulty: %s; distractor allowance: %d
                Company: %s

                Problem frame:
                - Business situation: %s
                - Observable symptom: %s
                - Consulting mandate: %s
                - Unknowns to validate: %s

                Canonical facts (the complete allowed truth):
                %s

                Evidence already captured by the learner:
                %s
                """.formatted(
                type.name(),
                engagement.getState().name(), type.name(), profile.level().name(), profile.distractorArtifactsPerAction(),
                lead.getCompanyName(),
                scenario.getBusinessSituation(), scenario.getObservableSymptom(), scenario.getConsultingMandate(),
                scenario.getUnknownsToValidate().isEmpty() ? "None specified" : String.join("; ", scenario.getUnknownsToValidate()),
                factLines,
                discoveredLines.isBlank() ? "None" : discoveredLines);
    }

    /** Adds controlled non-decisive context; the model never chooses game truth or distractor volume. */
    private List<ResearchArtifactResponse> shapeForDifficulty(Lead lead, EvidenceType type,
                                                                List<ResearchArtifactResponse> artifacts,
                                                                DifficultyProfile profile) {
        if (type == EvidenceType.OTHER || type == EvidenceType.HYPOTHESIS) return List.copyOf(artifacts);
        List<ResearchArtifactResponse> shaped = new ArrayList<>(artifacts.stream()
                .limit(Math.max(1, profile.researchArtifactsPerAction() - profile.distractorArtifactsPerAction()))
                .toList());
        for (int index = 0; index < profile.distractorArtifactsPerAction(); index++) {
            boolean ambiguity = index < Math.max(1, profile.contradictionCount() / 2);
            String title = ambiguity ? "Context signal requires validation" : "Adjacent industry signal";
            String summary = ambiguity
                    ? "A public signal suggests change activity, but does not confirm that the operating problem is resolved or funded."
                    : "%s organisations are discussing adjacent priorities that may not be material to this client decision."
                    .formatted(lead.getIndustry());
            shaped.add(new ResearchArtifactResponse("context-" + type.name().toLowerCase(Locale.ROOT) + "-" + index,
                    title, "Controlled market context", summary, type.name(), ConfidenceLevel.LOW.name(),
                    EvidenceOrigin.SCENARIO_CURATED.name(), LocalDate.now().minusDays(7 + index),
                    ambiguity ? 25 : 15,
                    List.of("public_description"), List.of(),
                    "Context only: test relevance against client evidence before adding it to the evidence board.",
                    blocks("context-" + type.name().toLowerCase(Locale.ROOT) + "-" + index,
                            summary,
                            "This source is deliberately non-decisive. Compare it with client-specific signals before using it to support a hypothesis.",
                            "Record uncertainty explicitly when evidence is adjacent rather than directly corroborated.")));
        }
        return List.copyOf(shaped);
    }

    private List<ResearchArtifactResponse> companyNews(Lead lead, Scenario scenario) {
        List<ResearchArtifactResponse> artifacts = new ArrayList<>();
        artifacts.add(artifact("company-news-1", lead.getCompanyName() + " reviews the operating conditions behind " + conciseSymptom(scenario),
                "Scenario-grounded operations analysis", scenario.getObservableSymptom(),
                EvidenceType.COMPANY_NEWS, ConfidenceLevel.HIGH, "business_situation", blocks("company-news-1",
                        scenario.getBusinessSituation(),
                        "The visible operating signal is clear: " + scenario.getObservableSymptom(),
                        availableSignalsParagraph(lead),
                        "For the consulting team, the immediate task is to " + lowerCaseFirst(scenario.getConsultingMandate()),
                        unknownsParagraph(scenario),
                        "This analysis identifies a client-specific operating issue. It does not prove root cause or approve a solution; use it to form a precise question for the client.")));
        artifacts.add(artifact("company-news-2", lead.getCompanyName() + " faces a decision point on " + conciseMandate(scenario),
                "Client operations desk", "A client-specific review of the decision pressure, operating signal and evidence still needed.",
                EvidenceType.COMPANY_NEWS, ConfidenceLevel.MEDIUM, "commercial_pressure", blocks("company-news-2",
                        "The decision context is shaped by " + scenario.getBusinessSituation(),
                        "The signal requiring investigation is " + lowerCaseFirst(scenario.getObservableSymptom()),
                        availableSignalsParagraph(lead),
                        "The immediate research test is whether that signal is creating a material service, reliability, cost or risk consequence for this client. The source does not establish that consequence by itself.",
                        "A defensible next step needs to support this mandate: " + lowerCaseFirst(scenario.getConsultingMandate()),
                        "Look for an explicit connection between the operating signal, the stakeholder who experiences it and the measure that would show improvement.",
                        unknownsParagraph(scenario))));
        return artifacts;
    }

    private List<ResearchArtifactResponse> stakeholderProfiles(Lead lead, Scenario scenario) {
        return List.of(
                artifact("stakeholder-1", lead.getDecisionMaker() != null ? lead.getDecisionMaker() : "Potential executive sponsor",
                        "Stakeholder profile",
                        "%s appears to be the most relevant stakeholder to validate pain, sponsorship and decision process."
                                .formatted(lead.getDecisionMaker() != null ? lead.getDecisionMaker() : "A senior operational leader"),
                        EvidenceType.STAKEHOLDER_PROFILE, ConfidenceLevel.HIGH, "decision_maker", blocks("stakeholder-1",
                                "%s appears to be the most relevant stakeholder to validate pain, sponsorship and decision process."
                                        .formatted(lead.getDecisionMaker() != null ? lead.getDecisionMaker() : "A senior operational leader"),
                                "The client is described publicly as: " + valueOr(lead.getPublicDescription(), "a business with operating priorities still to be explored"),
                                "The operating question to validate is: " + scenario.getObservableSymptom(),
                                availableSignalsParagraph(lead),
                                "Use the first conversation to distinguish stated priorities from the constraints that shape the decision process.",
                                "A senior title can indicate access, but it does not by itself establish decision authority, budget ownership, or willingness to sponsor change.",
                                "Capture the stakeholder's exact priority, success measure and concern before treating this profile as corroborated evidence.",
                                "A useful discovery question connects the visible operating signal to the mandate: " + scenario.getConsultingMandate())),
                artifact("stakeholder-2", "Commercial stakeholder influence",
                        "Stakeholder map",
                        "Budget and risk approval likely require commercial validation beyond the primary business sponsor.",
                        EvidenceType.STAKEHOLDER_PROFILE, ConfidenceLevel.MEDIUM, "stakeholder_complexity", blocks("stakeholder-2",
                                "Budget and risk approval likely require commercial validation beyond the primary business sponsor.",
                                "Treat the relationship map as a hypothesis: identify who can sponsor action, who may challenge it, and who controls the evidence needed for a decision.",
                                "Do not assume that a visible executive is the only stakeholder with influence.",
                                "For a practical next step, separate operational users, technical gatekeepers, commercial approvers and executive sponsors. Their incentives may not be aligned.",
                                unknownsParagraph(scenario))));
    }

    private List<ResearchArtifactResponse> financialSignals(Lead lead, Scenario scenario, boolean budgetVisible) {
        if (!budgetVisible) {
            return List.of(
                    artifact("financial-visibility-1", "Funding signals require discovery",
                            "Financial intelligence", "No client-confirmed budget detail is available in the research phase. "
                                    + "Use discovery to validate commercial priorities and investment appetite.",
                            EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, "public_description", blocks("financial-visibility-1",
                                    "No client-confirmed budget detail is available in the research phase.",
                                    "Use discovery to validate commercial priorities and investment appetite before treating any value case as funded.",
                                    "A useful next question is whether the operating issue has a measurable cost, service, risk or growth consequence.",
                                    "Absence of budget evidence is not evidence of no budget. Record it as an unknown and avoid inventing a figure or approval date.",
                                "The operating signal to quantify is: " + scenario.getObservableSymptom())),
                    artifact("financial-visibility-2", "Opportunity sizing remains provisional",
                            "Commercial analysis", "Public context suggests an opportunity, but no approved budget range is available. "
                                    + "Treat any estimate as a consultant assumption until the client validates it.",
                            EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, "public_description", blocks("financial-visibility-2",
                                    "Public context suggests an opportunity, but no approved budget range is available.",
                                    "Treat any estimate as a consultant assumption until the client validates it.",
                                    "Prioritize a baseline metric and a low-risk next step over a premature investment recommendation.",
                                    "The quality of this source improves only when an accountable stakeholder confirms the baseline, the materiality of the problem and the decision path.",
                                    "The resulting case should support the consulting mandate: " + scenario.getConsultingMandate())));
        }
        return List.of(
                artifact("financial-1", "Funding signal under review",
                        "Financial intelligence",
                        "%s. Any proposal should connect spend to measurable operational or risk reduction outcomes."
                                .formatted(lead.getBudgetSignal() != null ? lead.getBudgetSignal() : "Budget is not yet confirmed"),
                        EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, "budget_signal", blocks("financial-1",
                                valueOr(lead.getBudgetSignal(), "Budget is not yet confirmed"),
                                "Any proposal should connect spend to measurable operational or risk reduction outcomes.",
                                "Validate the owner, timing, approval route and conditions attached to any funding signal.",
                                "A funding signal can support an exploratory conversation; it is not permission to promise scope, benefits or a delivery date.",
                                "Anchor any proposed outcome to the visible operating signal: " + scenario.getObservableSymptom())),
                artifact("financial-2", "Potential opportunity sizing",
                        "Commercial analysis",
                        "The likely opportunity range is %s, but this should be validated through discovery before proposal."
                                .formatted(lead.getPotentialValueRange() != null ? lead.getPotentialValueRange() : "not yet confirmed"),
                        EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, "potential_value_range", blocks("financial-2",
                                "The likely opportunity range is %s, but this should be validated through discovery before proposal."
                                        .formatted(valueOr(lead.getPotentialValueRange(), "not yet confirmed")),
                                "An initial case is stronger when it identifies a credible baseline, a measurable outcome and the assumptions that still need client confirmation.",
                                "Use this range as a question to test with the client, not as a result to present as already achieved.",
                                unknownsParagraph(scenario))));
    }

    private List<ResearchArtifactResponse> technologySignals(Lead lead, Scenario scenario) {
        return List.of(
                artifact("technology-1", "Current technology environment",
                        "Technology research",
                        "%s. This may create integration, change-management and rollout risk."
                                .formatted(lead.getTechnologyStack() != null ? lead.getTechnologyStack() : "Technology stack is not yet confirmed"),
                        EvidenceType.TECHNOLOGY_INDICATOR, ConfidenceLevel.HIGH, "technology_stack", blocks("technology-1",
                                valueOr(lead.getTechnologyStack(), "Technology stack is not yet confirmed"),
                                "This may create integration, change-management and rollout risk.",
                                "Use discovery to establish the current operating workflow, dependencies and constraints before recommending a target architecture.",
                                "Technology names alone do not reveal data quality, ownership, security controls or the operational hand-offs that can make a change difficult.",
                                "The technology investigation should explain how the environment may contribute to: " + scenario.getObservableSymptom())),
                artifact("technology-2", "Implementation risk indicator",
                        "Architecture note",
                        "Legacy environments suggest phased migration, rollback planning and stakeholder training should be explored.",
                        EvidenceType.TECHNOLOGY_INDICATOR, ConfidenceLevel.MEDIUM, "implementation_risk", blocks("technology-2",
                                "Legacy environments suggest phased migration, rollback planning and stakeholder training should be explored.",
                                "This is an implementation question to validate, not a prescribed solution. Confirm where teams currently experience friction and which dependencies cannot be disrupted.",
                                "Document whether the constraint affects reliability, speed, compliance, cost or adoption. Those distinctions shape the right next question in the meeting.",
                                "The appropriate next step must still support the mandate: " + scenario.getConsultingMandate())));
    }

    private List<ResearchArtifactResponse> marketTrends(Lead lead) {
        return List.of(artifact("market-1", lead.getIndustry() + " modernisation pressure",
                "Market trend", "Comparable organisations are investing in interoperability, analytics and process visibility.",
                EvidenceType.MARKET_TREND, ConfidenceLevel.MEDIUM, "industry_trend"));
    }

    private ResearchArtifactResponse artifact(String id, String title, String sourceType, String summary,
                                              EvidenceType type, ConfidenceLevel confidence, String factKey) {
        return new ResearchArtifactResponse(id, title, sourceType, summary, type.name(), confidence.name(),
                EvidenceOrigin.SCENARIO_CURATED.name(), LocalDate.now().minusDays(14), relevanceFor(confidence), List.of(factKey), List.of(),
                "Generated from scenario-approved facts only; learner must decide whether it is relevant.", blocks(id, summary));
    }

    private ResearchArtifactResponse artifact(String id, String title, String sourceType, String summary,
                                              EvidenceType type, ConfidenceLevel confidence, String factKey,
                                              List<ResearchSourceBlock> blocks) {
        return new ResearchArtifactResponse(id, title, sourceType, summary, type.name(), confidence.name(),
                EvidenceOrigin.SCENARIO_CURATED.name(), LocalDate.now().minusDays(14), relevanceFor(confidence), List.of(factKey), List.of(),
                "Generated from scenario-approved facts only; learner must decide whether it is relevant.", blocks);
    }

    private List<ResearchSourceBlock> blocks(String sourceId, String... paragraphs) {
        List<ResearchSourceBlock> blocks = new ArrayList<>();
        for (int index = 0; index < paragraphs.length; index++) {
            String paragraph = paragraphs[index];
            if (paragraph == null || paragraph.isBlank()) continue;
            blocks.add(new ResearchSourceBlock(sourceId + "-block-" + (index + 1), ResearchSourceBlockType.PARAGRAPH, paragraph, null));
        }
        blocks.add(new ResearchSourceBlock(sourceId + "-record", ResearchSourceBlockType.CAPTION,
                "Scenario research record. Treat this source as evidence to assess, not a final client diagnosis.", null));
        return List.copyOf(blocks);
    }

    private String unknownsParagraph(Scenario scenario) {
        return scenario.getUnknownsToValidate().isEmpty()
                ? "The root cause, stakeholder constraints and viable next step still require validation."
                : "Questions still open for validation: " + String.join("; ", scenario.getUnknownsToValidate()) + ".";
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String availableSignalsParagraph(Lead lead) {
        List<String> signals = lead.getSignals().stream()
                .map(signal -> signal.getLabel())
                .filter(value -> !value.isBlank())
                .toList();
        return signals.isEmpty()
                ? "No additional client signal is available in this source. Treat the remaining question as open."
                : "The available client signals point to a connected research trail: " + String.join(" ", signals) + ".";
    }

    private String conciseSymptom(Scenario scenario) {
        return truncateAtWord(scenario.getObservableSymptom(), 88);
    }

    private String conciseMandate(Scenario scenario) {
        return truncateAtWord(scenario.getConsultingMandate(), 82);
    }

    private String truncateAtWord(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() <= maximumLength) return valueOr(value, "client operations");
        int boundary = value.lastIndexOf(' ', maximumLength - 3);
        return (boundary > 0 ? value.substring(0, boundary) : value.substring(0, maximumLength - 3)).trim() + "...";
    }

    private String lowerCaseFirst(String value) {
        if (value == null || value.isBlank()) return "validate the client problem before proposing a response";
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private int relevanceFor(ConfidenceLevel confidence) {
        return switch (confidence) {
            case HIGH -> 90;
            case MEDIUM -> 72;
            case LOW -> 30;
        };
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
