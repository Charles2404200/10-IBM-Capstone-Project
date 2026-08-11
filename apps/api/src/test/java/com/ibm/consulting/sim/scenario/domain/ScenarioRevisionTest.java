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
}
