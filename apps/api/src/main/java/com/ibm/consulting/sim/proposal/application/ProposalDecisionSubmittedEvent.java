package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.proposal.domain.ProposalDecisionSnapshot;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;

import java.util.List;
import java.util.UUID;

/** Published after the deterministic submission transaction commits. */
record ProposalDecisionSubmittedEvent(UUID engagementId, ProposalDraftContent content,
                                     List<ProposalSource> sources, PersonaProfile persona,
                                     ProposalDecisionSnapshot decision) {
    ProposalDecisionSubmittedEvent {
        sources = List.copyOf(sources);
    }
}
