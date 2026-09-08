package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.consulting.sim.proposal.domain.ClientDecisionOutcome;
import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ProposalDecisionSnapshot;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void draftSerializesNumericBudgetAndNullDecisionFields() throws Exception {
        ProposalResponse response = ProposalResponse.from(draft());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.path("budget").isNumber()).isTrue();
        assertThat(json.path("budget").decimalValue()).isEqualByComparingTo("125000.50");
        assertThat(json.path("clientDecisionOutcome").isNull()).isTrue();
        assertThat(json.path("decisionRationale").isNull()).isTrue();
        assertThat(json.path("submittedAt").isNull()).isTrue();
    }

    @Test
    void submittedButUnresolvedProposalKeepsDecisionFieldsNull() throws Exception {
        Proposal proposal = draft();
        proposal.submit();

        ProposalResponse response = ProposalResponse.from(proposal);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(response.submittedAt()).isNotNull();
        assertThat(json.path("submittedAt").isTextual()).isTrue();
        assertThat(json.path("clientDecisionOutcome").isNull()).isTrue();
        assertThat(json.path("decisionRationale").isNull()).isTrue();
    }

    @Test
    void wonAndLostProposalsExposeTheirActualClientOutcome() throws Exception {
        Proposal won = resolved(ClientDecisionOutcome.PILOT_APPROVED, "Pilot approved with controls");
        Proposal lost = resolved(ClientDecisionOutcome.REVISION_REQUESTED, "Revise the evidence case");
        JsonNode wonJson = objectMapper.readTree(objectMapper.writeValueAsBytes(ProposalResponse.from(won)));
        JsonNode lostJson = objectMapper.readTree(objectMapper.writeValueAsBytes(ProposalResponse.from(lost)));

        assertThat(wonJson.path("clientDecisionOutcome").asText()).isEqualTo("PILOT_APPROVED");
        assertThat(wonJson.path("decisionRationale").asText()).isEqualTo("Pilot approved with controls");
        assertThat(lostJson.path("clientDecisionOutcome").asText()).isEqualTo("REVISION_REQUESTED");
        assertThat(lostJson.path("decisionRationale").asText()).isEqualTo("Revise the evidence case");
    }

    private Proposal resolved(ClientDecisionOutcome outcome, String rationale) {
        Proposal proposal = draft();
        proposal.submit();
        proposal.resolve(new ProposalDecisionSnapshot(
                outcome, 72, 80, 75, List.of(), List.of(), List.of(), rationale), "Client response");
        return proposal;
    }

    private Proposal draft() {
        return Proposal.draft(UUID.randomUUID(), new ProposalDraftContent(
                "A grounded client problem", "A focused delivery strategy", List.of("Integration pilot"),
                new BigDecimal("125000.50"), 8, "MEDIUM", "Consultant estimate",
                List.of(), List.of(), List.of(), List.of(), List.of()));
    }
}
