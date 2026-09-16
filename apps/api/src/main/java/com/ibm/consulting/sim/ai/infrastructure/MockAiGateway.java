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
        String publicDescription = factValue(prompt, "public_description", "The client is assessing its current operating model.");
        String budgetSignal = factValue(prompt, "budget_signal", "No client-confirmed budget detail is visible in this research lane.");
        String signals = signalValues(prompt);
        List<String> signalFactIds = signalFactIds(prompt);

        if (situation.isBlank()) situation = factValue(prompt, "business_situation", "The operating context needs validation.");
        if (symptom.isBlank()) symptom = factValue(prompt, "observable_symptom", "The visible operating signal needs validation.");
        if (mandate.isBlank()) mandate = factValue(prompt, "consulting_mandate", "Build a grounded next step.");
        if (unknowns.isBlank()) unknowns = "The root cause, accountable owner and measurable baseline remain open.";
        if (signals.isBlank()) signals = "No additional client signal has been supplied; treat the remaining questions as open.";

        String sourceType = sourceTypeFor(lane);
        String laneFactId = laneFactId(lane);
        boolean laneFactAvailable = hasFact(prompt, laneFactId);
        boolean publicDescriptionAvailable = hasFact(prompt, "public_description");
        DocumentBlock laneSpecificBlock = laneFactAvailable
                ? fact(laneFact(lane, decisionMaker, technology, budgetSignal), laneFactId)
            : fact("The business situation frames the current research for this lane: " + situation,
            "business_situation");
        DocumentBlock publicDescriptionBlock = publicDescriptionAvailable
                ? fact(publicDescription, "public_description")
            : fact("The document keeps its company context within the reported business situation: " + situation,
            "business_situation");
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
                        fact(company + " is the organisation named in the scenario for this research brief.", "company_name"),
                        fact(situation, "business_situation"),
                        fact(symptom, "observable_symptom"),
                        fact(signals, signalFactIds),
                        fact(mandate, "consulting_mandate"),
                        laneSpecificBlock,
                        publicDescriptionBlock,
                        fact("The reported business situation, operating symptom and related client signals describe the current conditions surrounding "
                                + company + ". They provide a concrete frame for the issue without establishing a single confirmed cause or outcome.",
                            "business_situation"),
                        fact("The current picture combines the business situation of " + situation + " The operating signal is "
                                + symptom + " The related client signal is " + signals + " Taken together, these recorded points describe why the topic is material to "
                                + company + " while keeping the underlying mechanism and final decision outside the confirmed evidence.",
                            "observable_symptom"),
                        fact("The briefing keeps the business situation, operating signal and stated mandate in the same reporting frame. "
                            + "It records that " + mandate + " while preserving the client context supplied for " + company + ".",
                            "consulting_mandate")));
        String second = documentJson("mock-" + lane.toLowerCase(Locale.ROOT) + "-2", secondTitle, lane, sourceType,
                "MEDIUM", 0.74, publicDescription,
                List.of(
                        fact(company + " is the client organisation referenced by the current research material.", "company_name"),
                        publicDescriptionBlock,
                        fact(situation, "business_situation"),
                        fact(symptom, "observable_symptom"),
                        laneSpecificBlock,
                        fact(signals, signalFactIds),
                        fact(mandate, "consulting_mandate"),
                        fact("The reported client context links the business situation with the operating symptom and the lane-specific signal. "
                                + "It provides a different research angle without adding an unverified claim about cause, funding, authority or solution.",
                            List.of("business_situation", "observable_symptom")),
                        fact("The research material identifies " + situation + " alongside " + symptom + " It also records "
                                + signals + " These are distinct observations in the client context; their relationship is relevant to assess but is not stated as a confirmed causal finding.",
                            "observable_symptom"),
                        fact("The document retains the stated mandate, " + mandate + ", as part of the client research context. "
                            + "The narrative is anchored to the reported conditions rather than a separate claim about the outcome of that work.",
                            "consulting_mandate")));
        return "{\"artifacts\":[" + first + "," + second + "]}";
    }

    private static String documentJson(String id, String title, String lane, String sourceType, String reliability,
                                       double relevance, String summary, List<DocumentBlock> documentBlocks) {
        StringBuilder blocks = new StringBuilder();
        List<String> artifactFactIds = new java.util.ArrayList<>(List.of("company_name"));
        for (DocumentBlock block : documentBlocks.stream().limit(5).toList()) {
            if (!blocks.isEmpty()) blocks.append(',');
            blocks.append(blockJson("PARAGRAPH", block));
            block.factIds().forEach(factId -> { if (!artifactFactIds.contains(factId)) artifactFactIds.add(factId); });
        }
        return "{\"id\":\"" + escapeJson(id) + "\",\"title\":\"" + escapeJson(title)
                + "\",\"category\":\"" + escapeJson(lane) + "\",\"content\":\"" + escapeJson(summary)
                + "\",\"sourceType\":\"" + sourceType + "\",\"reliability\":\"" + reliability
                + "\",\"supportedFactIds\": [" + artifactFactIds.stream().map(factId -> "\"" + escapeJson(factId) + "\"").collect(java.util.stream.Collectors.joining(",")) + "]"
                + ",\"relevance\":" + relevance + ",\"confidence\":" + relevance + ",\"blocks\":[" + blocks + "]}";
    }

    private static String blockJson(String type, DocumentBlock block) {
        String factIds = block.factIds().stream().map(factId -> "\"" + escapeJson(factId) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"type\":\"" + type + "\",\"content\":\"" + escapeJson(block.content())
                + "\",\"purpose\":\"" + block.purpose() + "\",\"selectable\":" + block.selectable()
                + ",\"factIds\": [" + factIds + "]}";
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

    private static boolean hasFact(String prompt, String factKey) {
        return !lineValue(prompt, "- " + factKey + ":").isBlank();
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

    private static List<String> signalFactIds(String prompt) {
        List<String> ids = new java.util.ArrayList<>();
        for (String line : prompt.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("- signal_")) continue;
            int separator = trimmed.indexOf(':');
            if (separator > 2) ids.add(trimmed.substring(2, separator).trim());
        }
        return ids.isEmpty() ? List.of("business_situation") : List.copyOf(ids);
    }

    private static String sourceTypeFor(String lane) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "STAKEHOLDER_PROFILE";
            case "FINANCIAL_SIGNAL" -> "FINANCIAL_REPORT";
            case "TECHNOLOGY_INDICATOR" -> "TECHNOLOGY_NOTE";
            default -> "COMPANY_NEWS";
        };
    }

    private static String interpretedSignal(String lane, String company, String symptom, String technology) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "The available stakeholder context identifies a potential source of validation, but does not confirm decision authority or sponsorship.";
            case "FINANCIAL_SIGNAL" -> "The available commercial signals indicate an operating issue that may have material consequences; no funding decision is confirmed here.";
            case "TECHNOLOGY_INDICATOR" -> "The current systems may contribute to the observed operating signal, but the specific dependency has not been confirmed.";
            default -> "The stated business context and observable operating signal may be connected, but this source does not establish the causal mechanism.";
        };
    }

    private static String laneFact(String lane, String decisionMaker, String technology, String budgetSignal) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> decisionMaker;
            case "FINANCIAL_SIGNAL" -> budgetSignal;
            case "TECHNOLOGY_INDICATOR" -> technology;
            default -> technology;
        };
    }

    private static String laneFactId(String lane) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "decision_maker";
            case "FINANCIAL_SIGNAL" -> "budget_signal";
            case "TECHNOLOGY_INDICATOR" -> "technology_stack";
            default -> "technology_stack";
        };
    }

    private static String secondInterpretation(String lane, String symptom, String technology) {
        return switch (lane) {
            case "STAKEHOLDER_PROFILE" -> "The available role information identifies a potential source of validation, but not a confirmed decision maker.";
            case "FINANCIAL_SIGNAL" -> "The available commercial signal is not enough to establish a funded value case or quantify the client impact.";
            case "TECHNOLOGY_INDICATOR" -> "The stated technology environment and the operating symptom may be connected, but the evidence does not confirm a root technical cause.";
            default -> "The operating signal and current technology environment may be connected, but this source does not establish a root cause.";
        };
    }

    private static DocumentBlock fact(String content, String factId) {
        return fact(content, List.of(factId));
    }

    private static DocumentBlock fact(String content, List<String> factIds) {
        return new DocumentBlock(content, "FACT", factIds, true);
    }

    private record DocumentBlock(String content, String purpose, List<String> factIds, boolean selectable) {}

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
