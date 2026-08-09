package com.ibm.consulting.sim.proposal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalLifecycleTest {

    @Test
    void keepsSubmissionTimestampEmptyWhileTheProposalIsStillADraft() {
        Proposal proposal = Proposal.draft(java.util.UUID.randomUUID(), content("Initial problem statement"));

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(proposal.getSubmittedAt()).isNull();
    }

    @Test
    void keepsDraftEditableUntilSubmissionThenLocksIt() {
        Proposal proposal = Proposal.draft(java.util.UUID.randomUUID(), content("Initial problem statement"));
        proposal.updateDraft(content("Revised problem statement"));
        proposal.submit();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SUBMITTED);
        assertThat(proposal.getProblemStatement()).isEqualTo("Revised problem statement");
        assertThat(proposal.getSubmittedAt()).isNotNull();
    }

    private ProposalDraftContent content(String problem) {
        return new ProposalDraftContent(problem, "A grounded solution strategy", List.of("Pilot"), BigDecimal.TEN,
                1, "LOW", "Estimate", List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
