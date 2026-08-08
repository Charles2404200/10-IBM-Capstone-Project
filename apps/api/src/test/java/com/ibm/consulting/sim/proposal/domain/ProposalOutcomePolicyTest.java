package com.ibm.consulting.sim.proposal.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalOutcomePolicyTest {

    @Test
    void strongRelationshipAndEvidenceAlignmentWins() {
        List<String> evidence = List.of(
                "Client struggles with legacy inventory management systems",
                "Budget approved for digital transformation initiative");
        List<String> components = List.of(
                "Modernise inventory management platform",
                "Phased digital transformation roadmap",
                "Change management and training programme");

        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                "Address legacy inventory management challenges", components, evidence, 90, 85);

        assertThat(outcome.won()).isTrue();
        assertThat(outcome.alignmentScore()).isGreaterThanOrEqualTo(ProposalOutcomePolicy.WIN_THRESHOLD);
    }

    @Test
    void weakRelationshipAndNoEvidenceAlignmentLoses() {
        List<String> evidence = List.of("Client struggles with legacy inventory management systems");
        List<String> components = List.of("Generic consulting services");

        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                "Improve operations", components, evidence, 20, 15);

        assertThat(outcome.won()).isFalse();
        assertThat(outcome.alignmentScore()).isLessThan(ProposalOutcomePolicy.WIN_THRESHOLD);
    }

    @Test
    void emptyEvidenceYieldsZeroEvidenceScoreComponent() {
        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                "Some problem", List.of("Component A"), List.of(), 100, 100);

        // With no evidence discovered, evidence score contributes 0 regardless of relationship.
        // relationship (100) * 0.4 + evidence (0) * 0.4 + comprehensiveness (~33) * 0.2 ~= 46
        assertThat(outcome.alignmentScore()).isLessThan(ProposalOutcomePolicy.WIN_THRESHOLD);
    }

    @Test
    void alignmentScoreIsAlwaysWithinBounds() {
        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                "Problem", List.of(), List.of(), 0, 0);
        assertThat(outcome.alignmentScore()).isBetween(0, 100);
    }

    @Test
    void rationaleDescribesEachScoreComponent() {
        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                "Problem statement", List.of("Component"), List.of("Evidence note"), 50, 50);
        assertThat(outcome.rationale()).contains("Relationship").contains("evidence alignment").contains("comprehensiveness");
    }
}
