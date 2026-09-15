package com.ibm.consulting.sim.engagement.domain;

/** Immutable stored facts for deterministic lifecycle evaluation; null scores mean no data. */
public record LifecycleFacts(boolean leadSelected, long evidenceCount, boolean hasHypothesis,
        Integer researchConfidence, boolean outreachAccepted, boolean preparationReady,
        Integer preparationScore, boolean meetingStarted, boolean meetingCompleted,
        Integer trust, Integer interest, Integer patience, boolean proposalCreated,
        boolean proposalSubmitted, boolean clientDecisionAvailable, boolean assessmentAvailable) {
    public static LifecycleFacts empty() {
        return new LifecycleFacts(false,0,false,null,false,false,null,false,false,null,null,null,false,false,false,false);
    }
    public LifecycleFacts withLeadSelected(boolean selected) {
        return new LifecycleFacts(selected,evidenceCount,hasHypothesis,researchConfidence,outreachAccepted,
                preparationReady,preparationScore,meetingStarted,meetingCompleted,trust,interest,patience,
                proposalCreated,proposalSubmitted,clientDecisionAvailable,assessmentAvailable);
    }
}
