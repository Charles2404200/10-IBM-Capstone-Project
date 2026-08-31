package com.ibm.consulting.sim.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
import com.ibm.consulting.sim.lead.application.ClientIntelligenceFactGuard;
import com.ibm.consulting.sim.lead.application.ResearchArtifactResponse;
import com.ibm.consulting.sim.lead.domain.*;
import com.ibm.consulting.sim.meeting.domain.MeetingNaturalCompletionPolicy;
import com.ibm.consulting.sim.meeting.domain.MeetingSafetyPolicy;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateEngine;
import com.ibm.consulting.sim.outreach.domain.OutreachContentPolicy;
import com.ibm.consulting.sim.proposal.application.ProposalSource;
import com.ibm.consulting.sim.proposal.application.ProposalValidationEngine;
import com.ibm.consulting.sim.proposal.domain.*;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provider-independent regression gate for safety, grounding, progression and
 * outcome contracts. Fixtures are deliberately externalised so behavioural
 * changes require an explicit corpus review instead of hidden test rewrites.
 */
@Tag("ai-evaluation")
class AiEvaluationRegressionSuiteTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void researchEvidenceRemainsGroundedAndQualityBased() {
        JsonNode corpus = corpus("research");
        for (JsonNode scenario : corpus.path("cases")) {
            List<ResearchEvidence> evidence = evidence(scenario.path("evidence"));
            JsonNode expected = scenario.path("expected");

            assertThat(ResearchReadinessPolicy.isResearchComplete(evidence))
                    .as(scenario.path("id").asText())
                    .isEqualTo(expected.path("complete").asBoolean());
            assertBound(expected, "minimumConfidence", "maximumConfidence", ResearchReadinessPolicy.confidencePercent(evidence));
            assertNullableValue(expected, "decisionMaker", LeadIntelligencePolicy.decisionMaker(evidence).value());
            assertNullableValue(expected, "technology", LeadIntelligencePolicy.technologyStack(evidence).value());
            assertNullableValue(expected, "budget", LeadIntelligencePolicy.budgetSignal(evidence).value());
        }

        for (JsonNode factGuardCase : corpus.path("factGuardCases")) {
            List<String> allowed = strings(factGuardCase.path("allowedFacts"));
            List<String> cited = strings(factGuardCase.path("artifactFacts"));
            ResearchArtifactResponse artifact = new ResearchArtifactResponse(
                    "artifact-1", "Controlled intelligence", "SCENARIO_SOURCE", "Scenario-approved summary",
                    "COMPANY_NEWS", "HIGH", "AI_SYNTHESIZED", null, 90, cited, List.of(), "Grounded by canonical facts");
            Map<String, String> allowedFacts = allowed.stream().collect(Collectors.toMap(value -> value, value -> value));

            if (factGuardCase.path("expectedValid").asBoolean()) {
                ClientIntelligenceFactGuard.validate(List.of(artifact), allowedFacts);
            } else {
                assertThatThrownBy(() -> ClientIntelligenceFactGuard.validate(List.of(artifact), allowedFacts))
                        .isInstanceOf(AiValidationException.class)
                        .hasMessageContaining("Unsupported fact id");
            }
        }
    }

    @Test
    void outreachRemainsProfessionalRelevantAndActionable() {
        for (JsonNode scenario : corpus("outreach").path("cases")) {
            JsonNode ai = scenario.path("ai");
            OutreachEvaluationResult modelAssessment = new OutreachEvaluationResult(
                    "Provider assessment", ai.path("outcome").asText(), ai.path("personalisation").asInt(),
                    ai.path("relevance").asInt(), ai.path("clarity").asInt(), ai.path("callToAction").asInt(),
                    ai.path("trustDelta").asInt(), ai.path("interestDelta").asInt());
            OutreachEvaluationResult result = OutreachContentPolicy.apply(modelAssessment,
                    scenario.path("subject").asText(), scenario.path("body").asText(),
                    scenario.path("company").asText(), scenario.path("stakeholder").asText(), strings(scenario.path("evidence")));
            JsonNode expected = scenario.path("expected");

            assertThat(result.outcome()).as(scenario.path("id").asText()).isEqualTo(expected.path("outcome").asText());
            assertBound(expected, "minimumPersonalisation", "maximumPersonalisation", result.personalisation());
            assertBound(expected, "minimumCallToAction", "maximumCallToAction", result.callToAction());
            assertBound(expected, "minimumRelevance", "maximumRelevance", result.relevance());
            if (expected.has("exactPersonalisation")) assertThat(result.personalisation()).isEqualTo(expected.path("exactPersonalisation").asInt());
            if (expected.has("maximumTrustDelta")) assertThat(result.trustDelta()).isLessThanOrEqualTo(expected.path("maximumTrustDelta").asInt());
        }
    }

    @Test
    void liveMeetingScoringSafetyAndNaturalCompletionRemainHumanCentred() {
        JsonNode corpus = corpus("live-meeting");
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        for (JsonNode scenario : corpus.path("scoringCases")) {
            PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
            PersonaStateEngine.apply(state, turn(scenario), profile, scenario.path("message").asText(), scenario.path("turn").asInt());
            JsonNode exact = scenario.path("expected");
            JsonNode maximum = scenario.path("expectedMaximum");
            JsonNode minimum = scenario.path("expectedMinimum");
            if (!exact.isMissingNode()) {
                assertThat(state.getTrust()).as(scenario.path("id").asText()).isEqualTo(exact.path("trust").asInt());
                assertThat(state.getInterest()).isEqualTo(exact.path("interest").asInt());
                assertThat(state.getPatience()).isEqualTo(exact.path("patience").asInt());
            }
            if (!maximum.isMissingNode()) {
                assertThat(state.getTrust()).isLessThanOrEqualTo(maximum.path("trust").asInt());
                assertThat(state.getInterest()).isLessThanOrEqualTo(maximum.path("interest").asInt());
                assertThat(state.getPatience()).isLessThanOrEqualTo(maximum.path("patience").asInt());
            }
            if (!minimum.isMissingNode()) {
                assertThat(state.getTrust()).isGreaterThanOrEqualTo(minimum.path("trust").asInt());
                assertThat(state.getInterest()).isGreaterThanOrEqualTo(minimum.path("interest").asInt());
                assertThat(state.getPatience()).isGreaterThanOrEqualTo(minimum.path("patience").asInt());
            }
        }

        for (JsonNode scenario : corpus.path("safetyCases")) {
            var decision = MeetingSafetyPolicy.evaluate(scenario.path("message").asText(), PersonaState.initial(UUID.randomUUID()));
            if (scenario.path("expectedReason").isNull()) {
                assertThat(decision).as(scenario.path("id").asText()).isEmpty();
            } else {
                assertThat(decision).as(scenario.path("id").asText()).hasValueSatisfying(value ->
                        assertThat(value.reason().name()).isEqualTo(scenario.path("expectedReason").asText()));
            }
        }

        for (JsonNode scenario : corpus.path("completionCases")) {
            PersonaState readyState = PersonaState.initial(UUID.randomUUID());
            PersonaTurnResponse strongLegacyTurn = new PersonaTurnResponse("Client commitment", List.of(),
                    new PersonaStateDelta(50, 50, 50), List.of(), null, List.of(),
                    new PersonaTurnResponse.SafetyCheck(true, null));
            PersonaStateEngine.apply(readyState, strongLegacyTurn);
            PersonaStateEngine.apply(readyState, strongLegacyTurn);
            assertThat(MeetingNaturalCompletionPolicy.shouldConclude(readyState, strings(scenario.path("signals")), scenario.path("turns").asInt()))
                    .as(scenario.path("id").asText())
                    .isEqualTo(scenario.path("expectedComplete").asBoolean());
        }
    }

    @Test
    void proposalGuardRejectsInvalidEvidenceBeforeAClientDecision() {
        for (JsonNode scenario : corpus("proposal").path("cases")) {
            List<ProposalSource> sources = sources(scenario.path("sources"));
            List<String> blockingCodes = ProposalValidationEngine.validate(draft(scenario.path("draft")), sources).stream()
                    .filter(issue -> "BLOCKING".equals(issue.severity()))
                    .map(issue -> issue.code())
                    .toList();

            assertThat(blockingCodes).as(scenario.path("id").asText())
                    .containsExactlyInAnyOrderElementsOf(strings(scenario.path("expectedBlockingCodes")));
        }
    }

    private static JsonNode corpus(String name) {
        String path = "ai-evaluations/v1/" + name + ".json";
        try (var stream = AiEvaluationRegressionSuiteTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing AI evaluation corpus: " + path);
            return JSON.readTree(stream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load AI evaluation corpus: " + path, exception);
        }
    }

    private static List<ResearchEvidence> evidence(JsonNode entries) {
        UUID engagementId = UUID.nameUUIDFromBytes("evaluation-engagement".getBytes(StandardCharsets.UTF_8));
        UUID leadId = UUID.nameUUIDFromBytes("evaluation-lead".getBytes(StandardCharsets.UTF_8));
        Map<Integer, UUID> ids = new HashMap<>();
        for (JsonNode entry : entries) ids.put(entry.path("sequence").asInt(), UUID.nameUUIDFromBytes(("evidence-" + entry.path("sequence").asInt()).getBytes(StandardCharsets.UTF_8)));
        List<ResearchEvidence> result = new ArrayList<>();
        for (JsonNode entry : entries) {
            Set<UUID> supports = strings(entry.path("supports")).stream().map(Integer::parseInt).map(ids::get).filter(Objects::nonNull).collect(Collectors.toSet());
            result.add(ResearchEvidence.builder()
                    .engagementId(engagementId).leadId(leadId).sequenceNo(entry.path("sequence").asInt())
                    .evidenceType(EvidenceType.valueOf(entry.path("type").asText())).note(entry.path("note").asText())
                    .origin(EvidenceOrigin.valueOf(entry.path("origin").asText()))
                    .verificationStatus(EvidenceVerificationStatus.valueOf(entry.path("verification").asText()))
                    .confidence(ConfidenceLevel.valueOf(entry.path("confidence").asText()))
                    .relevanceScore(entry.path("relevance").asInt()).supportingEvidenceIds(supports).build());
        }
        return result;
    }

    private static PersonaTurnResponse turn(JsonNode scenario) {
        JsonNode delta = scenario.path("providerDelta");
        return new PersonaTurnResponse(scenario.path("clientResponse").asText(), strings(scenario.path("behaviours")),
                new PersonaStateDelta(delta.get(0).asInt(), delta.get(1).asInt(), delta.get(2).asInt()), List.of(), null,
                strings(scenario.path("signals")), new PersonaTurnResponse.SafetyCheck(true, null));
    }

    private static ProposalDraftContent draft(JsonNode node) {
        return new ProposalDraftContent(node.path("problem").asText(), node.path("solution").asText(), strings(node.path("components")),
                new BigDecimal(node.path("budget").asText("0")), node.path("timelineWeeks").asInt(), "UNCONFIRMED", node.path("budgetSource").asText(),
                outcomes(node.path("outcomes")), milestones(node.path("milestones")), risks(node.path("risks")), strings(node.path("assumptions")), links(node.path("links")));
    }

    private static List<ProposalSource> sources(JsonNode nodes) {
        List<ProposalSource> result = new ArrayList<>();
        for (JsonNode node : nodes) result.add(new ProposalSource(node.path("id").asText(), node.path("label").asText(), node.path("type").asText(), node.path("content").asText(), node.path("reliability").asText()));
        return result;
    }

    private static List<ProposalBusinessOutcome> outcomes(JsonNode nodes) {
        List<ProposalBusinessOutcome> result = new ArrayList<>();
        for (JsonNode node : nodes) result.add(new ProposalBusinessOutcome(node.path("outcome").asText(), node.path("metric").asText(), node.path("target").asText()));
        return result;
    }

    private static List<ProposalMilestone> milestones(JsonNode nodes) {
        List<ProposalMilestone> result = new ArrayList<>();
        for (JsonNode node : nodes) result.add(new ProposalMilestone(node.path("phase").asText(), node.path("duration").asText()));
        return result;
    }

    private static List<ProposalRisk> risks(JsonNode nodes) {
        List<ProposalRisk> result = new ArrayList<>();
        for (JsonNode node : nodes) result.add(new ProposalRisk(node.path("risk").asText(), node.path("severity").asText(), node.path("mitigation").asText()));
        return result;
    }

    private static List<ProposalEvidenceLink> links(JsonNode nodes) {
        List<ProposalEvidenceLink> result = new ArrayList<>();
        for (JsonNode node : nodes) result.add(new ProposalEvidenceLink(node.path("section").asText(), node.path("sourceId").asText()));
        return result;
    }

    private static List<String> strings(JsonNode nodes) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : nodes) result.add(node.asText());
        return result;
    }

    private static void assertNullableValue(JsonNode expected, String field, String actual) {
        if (!expected.has(field)) return;
        if (expected.path(field).isNull()) assertThat(actual).isNull();
        else assertThat(actual).isEqualTo(expected.path(field).asText());
    }

    private static void assertBound(JsonNode expected, String minimumField, String maximumField, int actual) {
        if (expected.has(minimumField)) assertThat(actual).isGreaterThanOrEqualTo(expected.path(minimumField).asInt());
        if (expected.has(maximumField)) assertThat(actual).isLessThanOrEqualTo(expected.path(maximumField).asInt());
    }
}
