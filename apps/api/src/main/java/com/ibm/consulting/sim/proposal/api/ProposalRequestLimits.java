package com.ibm.consulting.sim.proposal.api;

/** HTTP limits for learner-authored proposal content that is persisted and sent to AI services. */
final class ProposalRequestLimits {

    static final int NARRATIVE_MAX_LENGTH = 5_000;
    static final int ITEM_MAX_LENGTH = 1_000;
    static final int MAX_ITEMS = 20;
    static final int MAX_EVIDENCE_LINKS = 50;
    static final int EVIDENCE_SECTION_MAX_LENGTH = 60;
    static final int EVIDENCE_SOURCE_ID_MAX_LENGTH = 100;

    private ProposalRequestLimits() {}
}
