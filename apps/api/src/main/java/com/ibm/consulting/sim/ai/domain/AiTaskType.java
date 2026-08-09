package com.ibm.consulting.sim.ai.domain;

import java.util.Locale;

/**
 * Logical category of AI work, independent of any specific model or vendor.
 * The {@link com.ibm.consulting.sim.ai.application.AiProviderRouter} routes
 * every request by task type (capability-based routing) rather than by
 * provider name, so which vendor actually serves a given use-case is a
 * configuration concern, not a code concern.
 *
 * <ul>
 *   <li>{@link #CONVERSATION} — natural-language generation in a live,
 *       latency-sensitive context (persona dialogue, outreach client replies).
 *       Prefers low-latency free models; correctness of game state is enforced
 *       by the Simulation Engine (persona state clamping, fact-id allowlist),
 *       not by the model, so a weaker free model is an acceptable trade for speed.</li>
 *   <li>{@link #CLASSIFICATION} — small, structured-output judgements (intent,
 *       topic, quality signals). Cheap enough for any free model.</li>
 *   <li>{@link #ASSESSMENT} — end-of-engagement coaching narrative generation.
 *       Latency tolerant (2-5s is fine); prefers the strongest configured
 *       reasoning model — this is watsonx.ai/Granite's real job in this
 *       platform, not just a branding requirement (see design doc §Watsonx).</li>
 *   <li>{@link #EVIDENCE_EXTRACTION} — structured extraction/summarisation
 *       tasks (e.g. RAG-adjacent knowledge summarisation).</li>
 * </ul>
 */
public enum AiTaskType {
    CONVERSATION,
    CLASSIFICATION,
    CLIENT_INTELLIGENCE,
    ASSESSMENT,
    EVIDENCE_EXTRACTION;

    /**
     * Maps the free-text {@code useCase} label used at call sites today
     * ({@code "persona_dialogue"}, {@code "outreach_evaluation"},
     * {@code "assessment_feedback"}, ...) onto a routing task type. New use
     * cases default to {@link #CONVERSATION} (the safest general-purpose
     * category) unless explicitly mapped here.
     */
    public static AiTaskType fromUseCase(String useCase) {
        if (useCase == null) {
            return CONVERSATION;
        }
        return switch (useCase.toLowerCase(Locale.ROOT)) {
            case "assessment_feedback", "proposal_review", "proposal_client_decision" -> ASSESSMENT;
            case "client_intelligence" -> CLIENT_INTELLIGENCE;
            case "evidence_extraction", "knowledge_summarisation" -> EVIDENCE_EXTRACTION;
            case "question_classification", "intent_classification" -> CLASSIFICATION;
            default -> CONVERSATION;
        };
    }

    /** Lower-case key used to look up this task's provider priority list in configuration. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
