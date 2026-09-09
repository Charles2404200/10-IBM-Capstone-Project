package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.lead.domain.EvidenceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioRevisionTest {

    @Test
    void revisionKeepsLineageButGetsNewIdentityAndVersion() {
        Scenario published = Scenario.create("Distribution modernisation", "Retail", "Modernise fulfilment", 3);
        published.publish();

        Scenario revision = published.createRevision();

        assertThat(revision.getId()).isNotEqualTo(published.getId());
        assertThat(revision.getScenarioLineageId()).isEqualTo(published.getScenarioLineageId());
        assertThat(revision.getContentVersion()).isEqualTo(published.getContentVersion() + 1);
        assertThat(revision.getStatus()).isEqualTo(ScenarioStatus.DRAFT);
        assertThat(published.getStatus()).isEqualTo(ScenarioStatus.ACTIVE);
    }

    @Test
    void activeScenarioCannotBeEditedInPlace() {
        Scenario scenario = Scenario.create("Healthcare", "Healthcare", "Improve interoperability", 3);
        scenario.publish();

        assertThatThrownBy(() -> scenario.updateMetadata("Changed", "Healthcare", "Changed", 4))
                .isInstanceOf(Scenario.ScenarioNotEditableException.class);
    }

    @Test
    void authoringConfigRejectsDuplicateFactIdsAndRevealTargets() {
        CanonicalFact first = new CanonicalFact("funding", "Funding", "Funding is under review",
                EvidenceType.FINANCIAL_SIGNAL, true);
        CanonicalFact duplicate = new CanonicalFact("funding", "Budget", "Budget has not been approved",
                EvidenceType.FINANCIAL_SIGNAL, true);

        assertThatThrownBy(() -> new ScenarioAuthoringConfig(List.of(first, duplicate), List.of()))
                .isInstanceOf(InvalidScenarioAuthoringConfigException.class);

        RevealRule decisionMaker = new RevealRule(RevealTarget.DECISION_MAKER,
                Set.of(EvidenceType.STAKEHOLDER_PROFILE), 1);
        assertThatThrownBy(() -> new ScenarioAuthoringConfig(List.of(), List.of(decisionMaker, decisionMaker)))
                .isInstanceOf(InvalidScenarioAuthoringConfigException.class);
    }

    @Test
    void successCriteriaRoundTripWithoutDelimiterOrWhitespaceCorruption() {
        Scenario scenario = Scenario.create("Contract", "Technology", "Description", 3);
        List<String> criteria = List.of(
                "Reduce cost | protect quality",
                "Punctuation: commas, semicolons; and periods.",
                "Unicode: café — 東京",
                "  preserve authored whitespace  ");

        scenario.updateBriefing("Consultant", "Objective", criteria, 10);

        assertThat(scenario.getSuccessCriteria()).containsExactlyElementsOf(criteria);
    }

    @Test
    void emptySuccessCriteriaRemainAnEmptyList() {
        Scenario scenario = Scenario.create("Contract", "Technology", "Description", 3);
        scenario.updateBriefing("Consultant", "", List.of(), 10);

        assertThat(scenario.getSuccessCriteria()).isEmpty();
    }

    @Test
    void codecDistinguishesAnEmptyCriterionFromAnEmptyCriteriaList() {
        assertThat(SuccessCriteriaCodec.decode(SuccessCriteriaCodec.encode(List.of(""))))
                .containsExactly("");
        assertThat(SuccessCriteriaCodec.decode(SuccessCriteriaCodec.encode(List.of())))
                .isEmpty();
    }

    @Test
    void legacyPipeDelimitedCriteriaRemainReadable() {
        assertThat(SuccessCriteriaCodec.decode("Identify need|Build trust|Secure approval"))
                .containsExactly("Identify need", "Build trust", "Secure approval");
    }

    @Test
    void rubricDomainInvariantRejectsInvalidEntriesEvenOutsideHttp() {
        Scenario scenario = Scenario.create("Contract", "Technology", "Description", 3);
        java.util.Map<String, Integer> nullValue = new java.util.LinkedHashMap<>();
        nullValue.put("Communication", null);

        assertThatThrownBy(() -> scenario.updateRubricWeights(nullValue))
                .isInstanceOf(Scenario.InvalidRubricWeightsException.class);
        assertThatThrownBy(() -> scenario.updateRubricWeights(java.util.Map.of("Communication", -20, "Consulting", 120)))
                .isInstanceOf(Scenario.InvalidRubricWeightsException.class);
        assertThatThrownBy(() -> scenario.updateRubricWeights(java.util.Map.of("", 100)))
                .isInstanceOf(Scenario.InvalidRubricWeightsException.class);
    }
}
