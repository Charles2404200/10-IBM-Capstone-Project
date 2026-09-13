package com.ibm.consulting.sim.ai.infrastructure;

import com.ibm.consulting.sim.ai.domain.AiModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

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
            case "client_intelligence" -> clientIntelligenceReply(prompt);
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
            case "proposal_decision_explanation" -> """
                    {
                      "message": "The decision reflects the weighted client-alignment, evidence, commercial, delivery, risk and relationship dimensions already calculated by the simulation engine. Review the recorded strengths, concerns and conditions to understand the result."
                    }
                    """;
            case "proposal_counterfactual" -> """
                    {
                      "message": "The most valuable improvement is to validate each material claim with client-confirmed evidence, make the commercial assumptions explicit, and connect delivery controls to the client's stated risks."
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

    /**
     * The local gateway follows the same prompt contract as a real provider.
     * It is intentionally grounded in the prompt's canonical facts, not a
     * generic hard-coded story, so local demos and tests exercise the full
     * document-research workflow without an external model credential.
     */
    private static String clientIntelligenceReply(String prompt) {
        String lane = lineValue(prompt, "Research lane:");
        if (lane.isBlank()) lane = lineValue(prompt, "Research category:");
        if (lane.isBlank()) lane = "COMPANY_NEWS";
        String company = factValue(prompt, "company_name", "The client");
        String situation = lineValue(prompt, "- Business situation:");
        String symptom = lineValue(prompt, "- Observable symptom:");
        String mandate = lineValue(prompt, "- Consulting mandate:");
        String unknowns = lineValue(prompt, "- Unknowns to validate:");
        String decisionMaker = factValue(prompt, "decision_maker", "the accountable operational leader");
        String technology = factValue(prompt, "technology_stack", "the current operating systems");
        String signals = signalValues(prompt);

        if (situation.isBlank()) situation = factValue(prompt, "business_situation", "The operating context needs validation.");
        if (symptom.isBlank()) symptom = factValue(prompt, "observable_symptom", "The visible operating signal needs validation.");
        if (mandate.isBlank()) mandate = factValue(prompt, "consulting_mandate", "Build a grounded next step.");
        if (unknowns.isBlank()) unknowns = "The root cause, accountable owner and measurable baseline remain open.";
        if (signals.isBlank()) signals = "No additional client signal has been supplied; treat the remaining questions as open.";

        String sourceType = sourceTypeFor(lane);
        String firstTitle = switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "Stakeholder dossier: " + decisionMaker;
            case "FINANCIAL_SIGNAL" -> "Commercial readiness review for " + company;
            case "TECHNOLOGY_INDICATOR" -> "Technology dependency briefing: " + company;
            default -> company + " reviews the operating issue behind " + shorten(symptom, 74);
        };
        String secondTitle = switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "Decision influence and validation map";
            case "FINANCIAL_SIGNAL" -> "Value-case assumptions requiring client confirmation";
            case "TECHNOLOGY_INDICATOR" -> "Operational implications of the current technology environment";
            default -> "Client decision context: " + company;
        };

        String first = documentJson("mock-" + lane.toLowerCase(Locale.ROOT) + "-1", firstTitle, lane, sourceType,
                "HIGH", 0.9, situation,
                List.of(
                        situation,
                        "The client-specific operating signal is: " + symptom,
                        "The available scenario evidence points to this research trail: " + signals,
                        laneNarrative(lane, company, decisionMaker, technology, symptom),
                        "The consulting mandate is to " + lowerCaseFirst(mandate),
                        "Questions that still need client validation: " + unknowns),
                "Corroborate the operating signal with the accountable client owner and a measurable baseline before treating it as a confirmed diagnosis.");
        String second = documentJson("mock-" + lane.toLowerCase(Locale.ROOT) + "-2", secondTitle, lane, sourceType,
                "MEDIUM", 0.74, symptom,
                List.of(
                        "This source examines the decision pressure around " + lowerCaseFirst(situation),
                        "It does not assume a solution. Instead, it directs the learner to test how " + lowerCaseFirst(symptom),
                        "Known context includes " + signals,
                        "For this research lane, " + laneQuestion(lane, decisionMaker, technology),
                        "A credible next step should support " + lowerCaseFirst(mandate),
                        "The unresolved validation work is: " + unknowns),
                "Use this document as contextual evidence only after linking it to a client-specific consequence and an accountable stakeholder.");
        return "{\"artifacts\":[" + first + "," + second + "]}";
    }

    private static String documentJson(String id, String title, String lane, String sourceType, String reliability,
                                       double relevance, String summary, List<String> paragraphs, String caption) {
        StringBuilder blocks = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (!blocks.isEmpty()) blocks.append(',');
            blocks.append("{\"type\":\"PARAGRAPH\",\"content\":\"").append(escapeJson(paragraph)).append("\"}");
        }
        blocks.append(",{\"type\":\"CAPTION\",\"content\":\"").append(escapeJson(caption)).append("\"}");
        return "{\"id\":\"" + escapeJson(id) + "\",\"title\":\"" + escapeJson(title)
                + "\",\"category\":\"" + escapeJson(lane) + "\",\"content\":\"" + escapeJson(summary)
                + "\",\"sourceType\":\"" + sourceType + "\",\"reliability\":\"" + reliability
                + "\",\"supportedFactIds\":[\"company_name\",\"business_situation\",\"observable_symptom\",\"consulting_mandate\"]"
                + ",\"relevance\":" + relevance + ",\"confidence\":" + relevance + ",\"blocks\":[" + blocks + "]}";
    }

    private static String lineValue(String prompt, String marker) {
        int start = prompt.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = prompt.indexOf('\n', start);
        return prompt.substring(start, end < 0 ? prompt.length() : end).trim();
    }

    private static String factValue(String prompt, String factKey, String fallback) {
        return valueOr(lineValue(prompt, "- " + factKey + ":"), fallback);
    }

    private static String signalValues(String prompt) {
        StringBuilder values = new StringBuilder();
        for (String line : prompt.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("- signal_")) continue;
            int separator = trimmed.indexOf(':');
            if (separator < 0 || separator == trimmed.length() - 1) continue;
            if (!values.isEmpty()) values.append(' ');
            values.append(trimmed.substring(separator + 1).trim());
        }
        return values.toString();
    }

    private static String sourceTypeFor(String lane) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "STAKEHOLDER_PROFILE";
            case "FINANCIAL_SIGNAL" -> "FINANCIAL_REPORT";
            case "TECHNOLOGY_INDICATOR" -> "TECHNOLOGY_NOTE";
            default -> "COMPANY_NEWS";
        };
    }

    private static String laneNarrative(String lane, String company, String decisionMaker, String technology, String symptom) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> decisionMaker + " is the visible starting point for validating priorities, authority and the conditions under which teams can change the current workflow.";
            case "FINANCIAL_SIGNAL" -> "The commercial case should connect " + symptom + " to a measurable operating consequence before any funding assumption is treated as confirmed.";
            case "TECHNOLOGY_INDICATOR" -> "The current environment includes " + technology + ". Research should test which dependency contributes to the operating signal before recommending a target state.";
            default -> company + " needs evidence that connects the visible signal to a real client consequence, rather than a generic modernisation narrative.";
        };
    }

    private static String laneQuestion(String lane, String decisionMaker, String technology) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "the learner should confirm what " + decisionMaker + " can sponsor, who may challenge the change and whose evidence is needed for a decision";
            case "FINANCIAL_SIGNAL" -> "the learner should establish the baseline, materiality and approval conditions instead of assuming a funded value case";
            case "TECHNOLOGY_INDICATOR" -> "the learner should validate dependencies within " + technology + " and identify what cannot be disrupted during a pilot";
            default -> "the learner should link the operating issue to the stakeholder, consequence and measurement that make it decision-relevant";
        };
    }

    private static String shorten(String value, int maximumLength) {
        if (value.length() <= maximumLength) return value;
        int boundary = value.lastIndexOf(' ', maximumLength - 3);
        return (boundary > 0 ? value.substring(0, boundary) : value.substring(0, maximumLength - 3)) + "...";
    }

    private static String lowerCaseFirst(String value) {
        return value == null || value.isBlank() ? "the client problem" : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
