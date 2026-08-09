package com.ibm.consulting.sim.ai.infrastructure;

import com.ibm.consulting.sim.ai.domain.AiModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock AI gateway for local development and demo fallback.
 * Returns deterministic stub responses without calling any external service.
 *
 * <p>Gated by {@code app.ai.mock-mode} — a dedicated flag for the text-generation
 * orchestration layer, independent of {@code app.watsonx.mock-mode} (which only
 * gates the unrelated RAG embeddings gateway). When {@code app.ai.mock-mode=false},
 * {@link com.ibm.consulting.sim.ai.application.AiProviderRouter} takes over as the
 * {@link AiModelGateway} bean instead, fanning out to whichever real providers
 * (Gemini, OpenRouter, watsonx) are currently configured.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-mode", havingValue = "true", matchIfMissing = true)
public class MockAiGateway implements AiModelGateway {

    private static final Logger log = LoggerFactory.getLogger(MockAiGateway.class);

    @Override
    public String complete(String useCase, String prompt) {
        log.debug("MockAiGateway handling use-case: {}", useCase);
        return switch (useCase) {
            case "outreach_evaluation" -> """
                    {
                      "clientReply": "Thank you for your message. Your approach shows understanding of our challenges. Let's schedule a call.",
                      "outcome": "ACCEPTED",
                      "scores": { "personalisation": 75, "relevance": 80, "clarity": 70, "callToAction": 85 },
                      "reasonCodes": ["RELEVANT_PAIN_POINT", "CLEAR_VALUE_PROP"],
                      "relationshipStateDelta": { "trust": 5, "interest": 10 }
                    }
                    """;
            case "persona_dialogue" -> personaDialogueReply(prompt);
            case "client_intelligence" -> """
                    {
                      "artifacts": [
                        {
                          "id": "mock-client-intel-1",
                          "title": "Controlled client intelligence brief",
                          "category": "COMPANY_NEWS",
                          "content": "The organisation is signalling operational modernisation pressure based only on the provided canonical facts.",
                          "sourceType": "COMPANY_NEWS",
                          "reliability": "MEDIUM",
                          "supportedFactIds": ["company_name"],
                          "relevance": 0.75,
                          "confidence": 0.72
                        }
                      ]
                    }
                    """;
            case "assessment_feedback" -> """
                    {
                      "feedbackSummary": "You demonstrated solid discovery work and built trust steadily through the meeting. Your proposal reflected the client's stated priorities well.",
                      "strengths": ["Personalised outreach grounded in real research", "Asked open discovery questions before proposing solutions"],
                      "improvementAreas": ["Probe further on budget constraints before the meeting", "Address objections more directly when raised"]
                    }
                    """;
            case "meeting_debrief" -> """
                    {
                      "feedback": "You kept the conversation focused on the client situation and created a clear basis for the next step.",
                      "tips": ["Confirm the decision process explicitly.", "Quantify the operational impact before recommending a solution.", "End by summarising the agreed next step."]
                    }
                    """;
            case "proposal_review" -> """
                    {
                      "executiveFeedback": "The proposal has a useful structure. Tighten the commercial rationale and ensure each major claim is traceable to a client source.",
                      "improvementActions": ["Explain the basis for the estimate.", "Connect each KPI to a client priority.", "State how operational risk will be controlled."]
                    }
                    """;
            case "proposal_challenge" -> """
                    {
                      "concerns": ["What measurable outcome will this pilot deliver for the investment?", "How will you avoid disruption to current operations during implementation?", "Which client stakeholder owns the decision to proceed after the pilot?"]
                    }
                    """;
            case "proposal_client_decision" -> """
                    {
                      "message": "Thank you for the proposal. The recommendation reflects the priorities discussed, and we will take it through our internal decision process with the relevant stakeholders."
                    }
                    """;
            default -> "{}";
        };
    }

    private static final String[] GENERIC_REPLIES = {
            "Before we go further, can you tell me how you've approached this kind of transformation before?",
            "That's a fair point. What have you seen work well in similar situations?",
            "I appreciate you asking. Let me think about how best to frame this for you.",
            "That's something our team has been discussing internally as well.",
            "Good question — it depends a bit on how disruptive the change would be for our operations."
    };

    /**
     * Produces a persona reply that varies with the conversation instead of a single
     * hardcoded sentence (P0 fix — a static reply made every turn look like a
     * duplicated "Before we go further..." message). Cycles through a small set of
     * generic replies keyed off how many exchanges have happened, with a couple of
     * simple keyword-based specialisations so the mock still feels responsive
     * without needing a real model call.
     */
    private String personaDialogueReply(String prompt) {
        String learnerMessage = lastConsultantLine(prompt);
        String lower = learnerMessage.toLowerCase(java.util.Locale.ROOT);

        String response;
        List<String> facts = List.of();
        if (lower.contains("cost") || lower.contains("budget") || lower.contains("$") || lower.contains("impact")) {
            response = "Our internal estimates suggest this is costing us in the region of $2M annually in lost sales and rework.";
            facts = List.of("annual_impact_estimate");
        } else if (lower.contains("decision") || lower.contains("who") || lower.contains("stakeholder")
                || lower.contains("approve")) {
            response = "Ultimately I'd need sign-off from our COO, but I own the initial recommendation.";
            facts = List.of("decision_process");
        } else {
            int turnIndex = countOccurrences(prompt, "Consultant:");
            response = GENERIC_REPLIES[Math.floorMod(turnIndex, GENERIC_REPLIES.length)];
        }

        String factsJson = facts.stream()
                .map(f -> "\"" + f + "\"")
                .collect(java.util.stream.Collectors.joining(", "));

        return """
                {
                  "spokenResponse": "%s",
                  "detectedLearnerBehaviours": [],
                  "stateDelta": { "trust": 0, "interest": 2, "patience": -1 },
                  "factsDisclosed": [%s],
                  "objectionRaised": null,
                  "meetingSignals": ["client_is_curious"],
                  "safety": { "allowed": true, "reason": null }
                }
                """.formatted(escapeJson(response), factsJson);
    }

    private static String lastConsultantLine(String prompt) {
        String marker = "Consultant: ";
        int lastIndex = prompt.lastIndexOf(marker);
        return lastIndex < 0 ? "" : prompt.substring(lastIndex + marker.length()).trim();
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
