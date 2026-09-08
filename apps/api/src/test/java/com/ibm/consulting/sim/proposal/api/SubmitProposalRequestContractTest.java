package com.ibm.consulting.sim.proposal.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitProposalRequestContractTest {

    @Test
    void legacyPayloadDoesNotEnableWorkspaceValidation() {
        assertThat(request(null, null, null, null, null, null, null, null).usesWorkspaceContract()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("workspaceOnlyFields")
    void everyWorkspaceOnlyFieldEnablesWorkspaceValidation(ProposalController.SubmitProposalRequest request) {
        assertThat(request.usesWorkspaceContract()).isTrue();
    }

    private static Stream<ProposalController.SubmitProposalRequest> workspaceOnlyFields() {
        return Stream.of(
                request("strategy", null, null, null, null, null, null, null),
                request(null, "CONFIRMED", null, null, null, null, null, null),
                request(null, null, "Client", null, null, null, null, null),
                request(null, null, null, List.of(), null, null, null, null),
                request(null, null, null, null, List.of(), null, null, null),
                request(null, null, null, null, null, List.of(), null, null),
                request(null, null, null, null, null, null, List.of(), null),
                request(null, null, null, null, null, null, null, List.of()));
    }

    private static ProposalController.SubmitProposalRequest request(
            String solutionStrategy, String budgetConfidence, String budgetSource,
            List<ProposalController.OutcomeRequest> outcomes,
            List<ProposalController.MilestoneRequest> milestones,
            List<ProposalController.RiskRequest> risks,
            List<String> assumptions,
            List<ProposalController.EvidenceLinkRequest> evidenceLinks) {
        return new ProposalController.SubmitProposalRequest("Problem", List.of("Component"),
                BigDecimal.TEN, 4, solutionStrategy, budgetConfidence, budgetSource,
                outcomes, milestones, risks, assumptions, evidenceLinks);
    }
}
