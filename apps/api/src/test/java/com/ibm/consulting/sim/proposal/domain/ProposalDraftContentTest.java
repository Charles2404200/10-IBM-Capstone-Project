package com.ibm.consulting.sim.proposal.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalDraftContentTest {

    @Test
    void acceptsPartialLegacyDraftsWithoutNullCollectionEntries() {
        ProposalDraftContent content = new ProposalDraftContent(null, null, Arrays.asList("", null, " pilot "), null,
                0, null, null, List.of(new ProposalBusinessOutcome(null, null, null)),
                List.of(new ProposalMilestone(null, null)), List.of(new ProposalRisk(null, null, null)),
                Arrays.asList(null, " dependency "), List.of(new ProposalEvidenceLink(null, null)));

        assertThat(content.problemStatement()).isEmpty();
        assertThat(content.components()).containsExactly("pilot");
        assertThat(content.assumptions()).containsExactly("dependency");
        assertThat(content.budgetConfidence()).isEqualTo("UNCONFIRMED");
        assertThat(content.timelineWeeks()).isEqualTo(1);
    }
}
