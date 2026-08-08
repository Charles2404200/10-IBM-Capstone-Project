package com.ibm.consulting.sim.proposal.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class ProposalAlreadySubmittedException extends DomainException {
    public ProposalAlreadySubmittedException() {
        super("A proposal has already been submitted for this engagement");
    }
}
