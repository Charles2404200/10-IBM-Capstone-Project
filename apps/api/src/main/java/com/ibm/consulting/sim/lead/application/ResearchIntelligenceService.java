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
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlockPurpose;
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
    private static final String SOURCE_DECK_TEMPLATE_VERSION = "v4-long-form-fact-guarded";

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
    /** Hot engagement cache avoids database round-trips when learners revisit Research. */
    private final Cache<String, ResearchSourceDeckResponse> hotDeckCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(Duration.ofMinutes(5))
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
        String hotDeckKey = "deck:hot:" + SOURCE_DECK_TEMPLATE_VERSION + ":" + engagementId + ":" + userId;
        ResearchSourceDeckResponse hotCached = hotDeckCache.getIfPresent(hotDeckKey);
        if (hotCached != null) {
            return hotCached;
        }
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        Lead lead = loadLead(engagement);
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        Scenario scenario = loadScenario(engagement);
        ScenarioAuthoringConfig authoringConfig = authoringConfigService.forScenario(scenario);
        List<ResearchEvidence> discovered = evidenceRepository.findByEngagementId(engagementId);
        String deckKey = "deck:" + SOURCE_DECK_TEMPLATE_VERSION + ":"
                + cacheKey(lead, scenario, EvidenceType.COMPANY_NEWS, discovered, profile)
                + ":" + Integer.toHexString(authoringConfig.hashCode());
        ResearchSourceDeckResponse cached = completeDeckCache.getIfPresent(deckKey);
        if (cached != null) {
            hotDeckCache.put(hotDeckKey, cached);
            return cached;
        }

        List<EvidenceType> lanes = List.of(
                EvidenceType.COMPANY_NEWS,
                EvidenceType.STAKEHOLDER_PROFILE,
                EvidenceType.FINANCIAL_SIGNAL,
                EvidenceType.TECHNOLOGY_INDICATOR);
        Map<EvidenceType, CompletableFuture<List<ResearchArtifactResponse>>> futures = new java.util.LinkedHashMap<>();
        for (EvidenceType lane : lanes) {
            futures.put(lane, CompletableFuture.supplyAsync(
                    () -> generateImmediately(lead, scenario, lane, profile, authoringConfig, discovered),
                    researchSourceDeckExecutor));
        }

        Map<String, List<ResearchArtifactResponse>> sourcesByType = new java.util.LinkedHashMap<>();
        futures.forEach((lane, future) -> {
            try {
                sourcesByType.put(lane.name(), future.join());
            } catch (RuntimeException exception) {
                log.error("Research deck lane {} failed; returning the deterministic scenario pack", lane, exception);
                sourcesByType.put(lane.name(), templateGenerate(lead, scenario, lane, profile, authoringConfig));
            }
        });
        ResearchSourceDeckResponse response = new ResearchSourceDeckResponse(sourcesByType);
        completeDeckCache.put(deckKey, response);
        hotDeckCache.put(hotDeckKey, response);
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
        return generateImmediately(lead, scenario, type, profile, authoringConfig, discovered);
    }

    /**
     * Research is a reading workflow, so source availability cannot depend on an
     * external model. The first response is generated from canonical scenario facts
     * and cached; provider enrichment remains outside this latency-critical path.
     */
    private List<ResearchArtifactResponse> generateImmediately(Lead lead, Scenario scenario, EvidenceType type,
                                                                DifficultyProfile profile,
                                                                ScenarioAuthoringConfig authoringConfig,
                                                                List<ResearchEvidence> discovered) {
        String cacheKey = SOURCE_DECK_TEMPLATE_VERSION + ":" + cacheKey(lead, scenario, type, discovered, profile)
                + ":" + Integer.toHexString(authoringConfig.hashCode());
        List<ResearchArtifactResponse> cached = cachedArtifacts(cacheKey);
        if (cached != null) {
            return cached;
        }
        // Scenario configs created before the source-reader redesign contain short,
        // generic cards. Rebuilding the deck from current canonical facts prevents
        // legacy AI output from being presented as client evidence.
        List<ResearchArtifactResponse> sources = templateGenerate(lead, scenario, type, profile, authoringConfig);
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
        List<CanonicalFact> researchFacts = authoringConfig.canonicalFacts().stream()
                .filter(CanonicalFact::availableInResearch)
                .filter(fact -> fact.evidenceType() == type)
                .toList();
        return incorporateCanonicalFacts(base, researchFacts);
    }

    /**
     * Author-written facts strengthen the primary document for a research lane.
     * They are not emitted as short, generic pseudo-sources: that undermines the
     * learner's job of judging a coherent piece of client evidence.
     */
    private List<ResearchArtifactResponse> incorporateCanonicalFacts(List<ResearchArtifactResponse> artifacts,
                                                                       List<CanonicalFact> facts) {
        if (artifacts.isEmpty() || facts.isEmpty()) return List.copyOf(artifacts);

        ResearchArtifactResponse primary = artifacts.getFirst();
        List<ResearchSourceBlock> enrichedBlocks = new ArrayList<>(primary.blocks());
        int blockOffset = enrichedBlocks.size();
        for (int index = 0; index < facts.size(); index++) {
            CanonicalFact fact = facts.get(index);
            enrichedBlocks.add(new ResearchSourceBlock(
                    primary.id() + "-fact-" + (blockOffset + index + 1),
                    ResearchSourceBlockType.PARAGRAPH,
                    fact.value(),
                    fact.label(),
                    List.of(fact.id()),
                    true,
                    ResearchSourceBlockPurpose.FACT));
        }
        ResearchArtifactResponse enrichedPrimary = new ResearchArtifactResponse(
                primary.id(), primary.title(), primary.sourceType(), primary.summary(), primary.evidenceType(),
                primary.confidence(), primary.origin(), primary.publishedOn(), primary.relevanceScore(),
                mergeFactIds(primary.allowedFactKeys(), facts), primary.correlatesWithEvidence(),
                primary.relevanceRationale(), enrichedBlocks);

        List<ResearchArtifactResponse> enriched = new ArrayList<>(artifacts);
        enriched.set(0, enrichedPrimary);
        return List.copyOf(enriched);
    }

    private List<String> mergeFactIds(List<String> existing, List<CanonicalFact> additions) {
        return java.util.stream.Stream.concat(existing.stream(), additions.stream().map(CanonicalFact::id))
                .distinct()
                .toList();
    }

    private ResearchArtifactResponse toArtifact(ResearchSource source) {
        return new ResearchArtifactResponse("source-" + source.id(), source.title(), source.sourceType(), source.summary(),
                source.evidenceType().name(), source.confidence().name(), EvidenceOrigin.SCENARIO_CURATED.name(),
                LocalDate.now().minusDays(14), source.relevanceScore(), List.of("source-" + source.id()), List.of(),
                "Assess this source against the client problem before using it in your case.", source.effectiveBlocks());
    }

    private boolean hasGroundedBlocks(List<ResearchSourceBlock> blocks) {
        return blocks.stream().anyMatch(block -> Boolean.TRUE.equals(block.selectable())
                && !block.factIds().isEmpty()
                && block.factIds().stream().noneMatch("scenario_source"::equals));
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
                - Never write consultant instructions, recommended next steps, hypotheses, source-record notices, or meta commentary
                  inside a source document. The source is client intelligence, not a coaching response.
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
                        {"type": "PARAGRAPH", "content": "...", "purpose": "FACT", "selectable": true, "factIds": ["exact-fact-key"]},
                        {"type": "PARAGRAPH", "content": "...", "purpose": "INTERPRETATION", "selectable": true, "factIds": ["exact-fact-key"]},
                        {"type": "CAPTION", "content": "...", "purpose": "UNCERTAINTY", "selectable": false, "factIds": []}
                      ]
                    }
                  ]
                }

                Document requirements:
                - Return exactly 2 artifacts, each with 6 to 9 blocks and at least 450 characters across its blocks.
                - Each artifact must have 3 to 6 selectable blocks. Only FACT and INTERPRETATION blocks may be selectable.
                - Every selectable block must include one or more exact factIds. CONTEXT and UNCERTAINTY blocks are never selectable.
                - Use CONTEXT only for factual background and UNCERTAINTY only for an explicitly unresolved scenario fact.
                - Do not use GUIDANCE blocks in this response. Never turn the consulting mandate into an instruction to the learner.
                - COMPANY_NEWS: write an industry-news analysis with a headline, a client-specific operating signal,
                  a clearly-labelled fact-linked interpretation, and an explicitly open question.
                - STAKEHOLDER_PROFILE: write a credible dossier that separates known role/context from priorities,
                  decision influence, and questions to validate.
                - FINANCIAL_SIGNAL: write an analyst or internal finance brief that separates confirmed commercial
                  signals from assumptions, baseline questions, and approval uncertainty.
                - TECHNOLOGY_INDICATOR: write a technical briefing that explains known systems, possible operational
                  implications, dependencies to validate, and the appropriate discovery focus.
                - Use METRIC only for a numeric fact present in the canonical facts; otherwise use PARAGRAPH or CAPTION.
                - Add exactly one non-selectable CAPTION per artifact that states the unresolved client uncertainty only.
                - The second source must offer a different evidence angle; do not paraphrase or repeat the first source.

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

    /**
     * Difficulty must be authored as a scenario fact or a scenario-specific contradiction.
     * Generic distractor documents train learners to collect filler, so this layer now
     * deliberately preserves only the sources grounded in the current scenario.
     */
    private List<ResearchArtifactResponse> shapeForDifficulty(Lead lead, EvidenceType type,
                                                                List<ResearchArtifactResponse> artifacts,
                                                                DifficultyProfile profile) {
        return List.copyOf(artifacts);
    }

    private List<ResearchArtifactResponse> companyNews(Lead lead, Scenario scenario) {
        List<ResearchSourceBlock> operatingStory = documentBlocks("company-news-1",
                factualNarrative("The commercial setting described in the current record is clear: ",
                        scenario.getBusinessSituation(), "business_situation"),
                factualNarrative("The operating condition now drawing attention is equally specific: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                narratedSignal(lead, 0, "A related client signal reported alongside the operating picture is: "),
                factualNarrative("The available system context is recorded as follows: ", valueOr(lead.getTechnologyStack(), ""),
                        "technology_stack", "No confirmed technology environment is available in this report."),
                factualNarrative("The client is described in the available background as: ", valueOr(lead.getPublicDescription(), ""),
                        "public_description", "No public company description has been confirmed for this report."),
                interpretation("Read together, the reported commercial priority, operating symptom and system context describe a possible chain of pressure. They do not, however, establish which process step or decision is the root cause. That distinction matters before a response is treated as a client need.",
                        "business_situation", "observable_symptom", "technology_stack"),
                interpretation("The reported symptom is material because it is attached to an explicit business outcome rather than an isolated operational complaint. The current evidence supports investigating the connection; it does not support claiming that the connection has been proven.",
                        "business_situation", "observable_symptom"),
                uncertainty(unknownsParagraph(scenario)));
        List<ResearchSourceBlock> contextStory = documentBlocks("company-news-2",
                factualNarrative("The wider company context in the source material is: ", valueOr(lead.getPublicDescription(), ""),
                        "public_description", "No public company description has been confirmed for this report."),
                factualNarrative("Against that backdrop, leaders are dealing with this operating reality: ",
                        scenario.getBusinessSituation(), "business_situation"),
                narratedSignal(lead, 1, "A second reported signal is: "),
                factualNarrative("The visible performance or operating symptom is: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                factualNarrative("The systems and data context currently available is: ", valueOr(lead.getTechnologyStack(), ""),
                        "technology_stack", "No confirmed technology environment is available in this report."),
                interpretation("This account gives a coherent reason to investigate the operating signal in more detail. It should not be read as proof of a technical failure, an approved initiative, or a preferred solution, because none of those conclusions appears in the verified record.",
                        "business_situation", "observable_symptom", "technology_stack"),
                uncertainty(unknownsParagraph(scenario)));
        return List.of(
                artifact("company-news-1", sourceHeadline(scenario.getObservableSymptom()),
                        "Industry operations journal", scenario.getObservableSymptom(), EvidenceType.COMPANY_NEWS,
                        ConfidenceLevel.HIGH, operatingStory),
                artifact("company-news-2", sourceHeadline(scenario.getBusinessSituation()),
                        "Client operations review", "A fact-grounded view of the business situation and operating signal.",
                        EvidenceType.COMPANY_NEWS, ConfidenceLevel.MEDIUM, contextStory));
    }

    private List<ResearchArtifactResponse> stakeholderProfiles(Lead lead, Scenario scenario) {
        List<ResearchSourceBlock> namedProfile = documentBlocks("stakeholder-1",
                factualNarrative("The named contact in the available client record is: ", valueOr(lead.getDecisionMaker(), ""),
                        "decision_maker", "No named stakeholder has been confirmed for this dossier."),
                factualNarrative("The organisation associated with this role is described as: ", valueOr(lead.getPublicDescription(), ""),
                        "public_description", "No public company description has been confirmed for this dossier."),
                factualNarrative("The operating concern in the stakeholder's reported context is: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                factualNarrative("The stated business context around that concern is: ",
                        scenario.getBusinessSituation(), "business_situation"),
                narratedSignal(lead, 0, "A related signal in the client record is: "),
                interpretation("The role and operating context make this a relevant profile for understanding the client situation. That is an assessment of relevance, not confirmation that this person owns a budget, signs off a decision, or will sponsor change.",
                        "decision_maker", "business_situation", "observable_symptom"),
                interpretation("The record links this role to a live business issue, but it does not state the stakeholder's success measure, concerns, or authority. Those are separate facts that must be established through subsequent discovery rather than inferred from a senior title.",
                        "decision_maker", "observable_symptom"),
                uncertainty("Decision authority, sponsorship and the stakeholder's success measure remain unconfirmed."));
        List<ResearchSourceBlock> influenceContext = documentBlocks("stakeholder-2",
                factualNarrative("The business situation that frames the stakeholder landscape is: ",
                        scenario.getBusinessSituation(), "business_situation"),
                factualNarrative("The evidence currently visible at operating level is: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                narratedSignal(lead, 1, "A further reported client signal is: "),
                factualNarrative("The available technology or data context is: ", valueOr(lead.getTechnologyStack(), ""),
                        "technology_stack", "No confirmed technology owner or environment is available in this dossier."),
                factualNarrative("The financial context visible at this stage is: ", valueOr(lead.getBudgetSignal(), ""),
                        "budget_signal", "No client-confirmed budget owner or funding decision is available in this dossier."),
                interpretation("The available facts identify several domains that may shape the decision process. They do not identify the approval path, and they should not be used to assign influence or decision rights to a stakeholder without direct evidence.",
                        "business_situation", "observable_symptom", "technology_stack"),
                uncertainty(unknownsParagraph(scenario)));
        return List.of(
                artifact("stakeholder-1", valueOr(lead.getDecisionMaker(), "Stakeholder context under review"),
                        "Stakeholder dossier", "Known role and client signals, separated from the decisions still to validate.",
                        EvidenceType.STAKEHOLDER_PROFILE, ConfidenceLevel.HIGH, namedProfile),
                artifact("stakeholder-2", sourceHeadline(scenario.getBusinessSituation()),
                        "Stakeholder landscape", "Known client context and unresolved decision ownership.",
                        EvidenceType.STAKEHOLDER_PROFILE, ConfidenceLevel.MEDIUM, influenceContext));
    }

    private List<ResearchArtifactResponse> financialSignals(Lead lead, Scenario scenario, boolean budgetVisible) {
        List<ResearchSourceBlock> funding = documentBlocks("financial-1",
                budgetVisible ? factualNarrative("The client-confirmed budget signal currently available is: ", valueOr(lead.getBudgetSignal(), ""), "budget_signal")
                        : uncertainty("No client-confirmed budget signal is available at this stage."),
                factualNarrative("The potential value range recorded for this opportunity is: ", valueOr(lead.getPotentialValueRange(), ""),
                        "potential_value_range", "No confirmed value range is available at this stage."),
                factualNarrative("The operating symptom with possible commercial consequences is: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                factualNarrative("The business context that makes the signal material is: ",
                        scenario.getBusinessSituation(), "business_situation"),
                narratedSignal(lead, 0, "A related commercial or operating signal is: "),
                interpretation("The available facts support further sizing because an operating symptom is connected to a stated business priority. They do not establish a financial baseline, a funded business case, or an approved investment; those are materially different claims.",
                        "business_situation", "observable_symptom", "potential_value_range"),
                interpretation("The value range should be treated as an opportunity frame, not as realised benefit. The source does not specify the baseline, the accountable owner, or the timing assumptions needed to convert that range into a credible impact estimate.",
                        "potential_value_range"),
                uncertainty(unknownsParagraph(scenario)));
        List<ResearchSourceBlock> commercialContext = documentBlocks("financial-2",
                factualNarrative("The commercial priority described in the client record is: ",
                        scenario.getBusinessSituation(), "business_situation"),
                factualNarrative("The operating condition affecting that priority is: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                narratedSignal(lead, 1, "Another signal relevant to commercial materiality is: "),
                factualNarrative("The available company background is: ", valueOr(lead.getPublicDescription(), ""),
                        "public_description", "No public commercial context has been confirmed for this report."),
                factualNarrative("The current range or budget context is: ", valueOr(lead.getPotentialValueRange(), ""),
                        "potential_value_range", "No confirmed value range is available at this stage."),
                interpretation("The operating issue may have commercial implications, but the record provides neither an agreed baseline nor an approval route. It therefore supports a financial research question, not a claim that funding or payback is already secured.",
                        "business_situation", "observable_symptom", "potential_value_range"),
                uncertainty("Commercial materiality and investment timing remain to be validated."));
        return List.of(
                artifact("financial-1", budgetVisible ? sourceHeadline(valueOr(lead.getBudgetSignal(), scenario.getObservableSymptom())) : sourceHeadline(scenario.getObservableSymptom()),
                        "Financial intelligence", "Confirmed commercial signals and explicit gaps in the available record.",
                        EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, funding),
                artifact("financial-2", sourceHeadline(scenario.getBusinessSituation()),
                        "Commercial analysis", "A fact-grounded view of the commercial and operating context.",
                        EvidenceType.FINANCIAL_SIGNAL, ConfidenceLevel.MEDIUM, commercialContext));
    }

    private List<ResearchArtifactResponse> technologySignals(Lead lead, Scenario scenario) {
        List<ResearchSourceBlock> environment = documentBlocks("technology-1",
                factualNarrative("The current technology and data environment is described as: ", valueOr(lead.getTechnologyStack(), ""),
                        "technology_stack", "No confirmed technology environment is available in this brief."),
                factualNarrative("The business-facing symptom that this environment may relate to is: ",
                        scenario.getObservableSymptom(), "observable_symptom"),
                factualNarrative("The wider business situation is: ", scenario.getBusinessSituation(), "business_situation"),
                narratedSignal(lead, 0, "A related client signal is: "),
                factualNarrative("The available company background is: ", valueOr(lead.getPublicDescription(), ""),
                        "public_description", "No public operating-system context has been confirmed for this brief."),
                interpretation("The environment may be relevant because the operating symptom sits alongside the stated data and system context. The available record does not establish a technical root cause, integration failure, or target architecture, so each of those remains a hypothesis rather than a source fact.",
                        "technology_stack", "observable_symptom", "business_situation"),
                interpretation("The source is useful for locating the technical questions inside the client context. It is not evidence that technology alone caused the commercial outcome, because the relationship has not yet been demonstrated in the scenario facts.",
                        "technology_stack", "observable_symptom"),
                uncertainty(unknownsParagraph(scenario)));
        List<ResearchSourceBlock> operatingDependency = documentBlocks("technology-2",
                factualNarrative("The client is operating within this business context: ", scenario.getBusinessSituation(), "business_situation"),
                factualNarrative("The signal that requires investigation is: ", scenario.getObservableSymptom(), "observable_symptom"),
                factualNarrative("The named systems or data conditions are: ", valueOr(lead.getTechnologyStack(), ""),
                        "technology_stack", "No named system dependency has been confirmed in this brief."),
                narratedSignal(lead, 1, "An additional signal associated with the operating environment is: "),
                factualNarrative("The commercial context currently available is: ", valueOr(lead.getBudgetSignal(), ""),
                        "budget_signal", "No client-confirmed investment decision is available in this brief."),
                interpretation("The relationship between the environment and the client outcome remains an evidence question rather than a confirmed architecture diagnosis. The facts justify tracing the relevant hand-offs and data dependencies, but they do not identify a system owner or prescribe a technical response.",
                        "business_situation", "observable_symptom", "technology_stack"),
                uncertainty("System ownership, data flow and non-disruptable dependencies remain unconfirmed."));
        return List.of(
                artifact("technology-1", sourceHeadline(valueOr(lead.getTechnologyStack(), scenario.getObservableSymptom())), "Technology due diligence brief",
                        "Known system context and the operating signal it may relate to.", EvidenceType.TECHNOLOGY_INDICATOR,
                        ConfidenceLevel.HIGH, environment),
                artifact("technology-2", sourceHeadline(scenario.getBusinessSituation()), "Architecture research note",
                        "Available technical context and explicitly unresolved dependencies.",
                        EvidenceType.TECHNOLOGY_INDICATOR, ConfidenceLevel.MEDIUM, operatingDependency));
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

    private ResearchArtifactResponse artifact(String id, String title, String sourceType, String summary,
                                              EvidenceType type, ConfidenceLevel confidence,
                                              List<ResearchSourceBlock> blocks) {
        List<String> factIds = blocks.stream()
                .flatMap(block -> block.factIds().stream())
                .filter(factId -> !factId.isBlank())
                .distinct()
                .toList();
        return new ResearchArtifactResponse(id, title, sourceType, summary, type.name(), confidence.name(),
                EvidenceOrigin.SCENARIO_CURATED.name(), LocalDate.now().minusDays(14), relevanceFor(confidence),
                factIds.isEmpty() ? List.of("business_situation") : factIds, List.of(),
                "Source content is grounded in scenario facts; unresolved items are marked separately.", blocks);
    }

    private List<ResearchSourceBlock> documentBlocks(String sourceId, SourceBlockSeed... seeds) {
        List<ResearchSourceBlock> blocks = new ArrayList<>();
        for (int index = 0; index < seeds.length; index++) {
            SourceBlockSeed seed = seeds[index];
            if (seed.content() == null || seed.content().isBlank()) continue;
            blocks.add(new ResearchSourceBlock(sourceId + "-block-" + (index + 1),
                    seed.type(), seed.content(), null, seed.factIds(), seed.selectable(), seed.purpose()));
        }
        return List.copyOf(blocks);
    }

    private SourceBlockSeed fact(String content, String... factIds) {
        return new SourceBlockSeed(ResearchSourceBlockType.PARAGRAPH, content, List.of(factIds), true, ResearchSourceBlockPurpose.FACT);
    }

    private SourceBlockSeed factualNarrative(String leadIn, String content, String factId) {
        return factualNarrative(leadIn, content, factId, "No verified detail is available in this document.");
    }

    private SourceBlockSeed factualNarrative(String leadIn, String content, String factId, String unavailableMessage) {
        return content == null || content.isBlank()
                ? context(unavailableMessage)
                : fact(leadIn + content, factId);
    }

    private SourceBlockSeed optionalFact(String content, String factId, String unavailableMessage) {
        return content == null || content.isBlank()
                ? context(unavailableMessage)
                : fact(content, factId);
    }

    private SourceBlockSeed signalFact(Lead lead, int index) {
        List<com.ibm.consulting.sim.lead.domain.LeadSignal> signals = lead.getSignals().stream()
                .filter(signal -> signal.getLabel() != null && !signal.getLabel().isBlank())
                .toList();
        if (signals.isEmpty()) return context("No additional client signal is available in this source.");
        var signal = signals.get(Math.floorMod(index, signals.size()));
        return fact(signal.getLabel(), "signal_" + signal.getCategory().toLowerCase(Locale.ROOT));
    }

    private SourceBlockSeed narratedSignal(Lead lead, int index, String leadIn) {
        List<com.ibm.consulting.sim.lead.domain.LeadSignal> signals = lead.getSignals().stream()
                .filter(signal -> signal.getLabel() != null && !signal.getLabel().isBlank())
                .toList();
        if (signals.isEmpty()) return context("No additional client signal is available in this document.");
        var signal = signals.get(Math.floorMod(index, signals.size()));
        return fact(leadIn + signal.getLabel(), "signal_" + signal.getCategory().toLowerCase(Locale.ROOT));
    }

    private SourceBlockSeed interpretation(String content, String... factIds) {
        return new SourceBlockSeed(ResearchSourceBlockType.PARAGRAPH, content, List.of(factIds), true,
                ResearchSourceBlockPurpose.INTERPRETATION);
    }

    private SourceBlockSeed context(String content, String... factIds) {
        return new SourceBlockSeed(ResearchSourceBlockType.PARAGRAPH, content, List.of(factIds), false,
                ResearchSourceBlockPurpose.CONTEXT);
    }

    private SourceBlockSeed uncertainty(String content) {
        return new SourceBlockSeed(ResearchSourceBlockType.CAPTION, content, List.of(), false,
                ResearchSourceBlockPurpose.UNCERTAINTY);
    }

    private List<ResearchSourceBlock> blocks(String sourceId, String... paragraphs) {
        List<ResearchSourceBlock> fallbackBlocks = new ArrayList<>();
        for (int index = 0; index < paragraphs.length; index++) {
            String paragraph = paragraphs[index];
            if (paragraph == null || paragraph.isBlank()) continue;
            fallbackBlocks.add(new ResearchSourceBlock(sourceId + "-block-" + (index + 1), ResearchSourceBlockType.PARAGRAPH,
                    paragraph, null, List.of(), false, ResearchSourceBlockPurpose.CONTEXT));
        }
        return List.copyOf(fallbackBlocks);
    }

    private String unknownsParagraph(Scenario scenario) {
        return scenario.getUnknownsToValidate().isEmpty()
                ? "The root cause, stakeholder constraints and viable next step still require validation."
                : "Questions still open for validation: " + String.join("; ", scenario.getUnknownsToValidate()) + ".";
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SourceBlockSeed(ResearchSourceBlockType type, String content, List<String> factIds,
                                   boolean selectable, ResearchSourceBlockPurpose purpose) {}

    private String conciseSymptom(Scenario scenario) {
        return truncateAtWord(scenario.getObservableSymptom(), 88);
    }

    /** A deck headline may abbreviate a fact, but may never introduce a new claim. */
    private String sourceHeadline(String fact) {
        return truncateAtWord(fact, 112);
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
