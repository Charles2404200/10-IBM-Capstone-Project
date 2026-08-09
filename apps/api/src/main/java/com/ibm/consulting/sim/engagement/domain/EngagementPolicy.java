package com.ibm.consulting.sim.engagement.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines all valid state transitions for the engagement lifecycle.
 * Pure domain logic — no Spring dependencies.
 */
public final class EngagementPolicy {

    private static final Map<EngagementState, Set<EngagementState>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(EngagementState.QUALIFYING,
                    EnumSet.of(EngagementState.CLIENT_INTELLIGENCE)),
            Map.entry(EngagementState.CLIENT_INTELLIGENCE,
                    EnumSet.of(EngagementState.HYPOTHESIS_READY)),
            Map.entry(EngagementState.HYPOTHESIS_READY,
                    EnumSet.of(EngagementState.OUTREACHING)),
            Map.entry(EngagementState.OUTREACHING,
                    EnumSet.of(EngagementState.OUTREACHING, EngagementState.MEETING_SECURED)),
            Map.entry(EngagementState.MEETING_SECURED,
                    EnumSet.of(EngagementState.PREPARING)),
            Map.entry(EngagementState.PREPARING,
                    EnumSet.of(EngagementState.PREPARING, EngagementState.IN_MEETING)),
            Map.entry(EngagementState.IN_MEETING,
                    EnumSet.of(EngagementState.DISCOVERY_COMPLETE, EngagementState.MEETING_FAILED)),
            Map.entry(EngagementState.DISCOVERY_COMPLETE,
                    EnumSet.of(EngagementState.PROPOSAL_DRAFT)),
            Map.entry(EngagementState.PROPOSAL_DRAFT,
                    EnumSet.of(EngagementState.PROPOSAL_SUBMITTED)),
            Map.entry(EngagementState.PROPOSAL_SUBMITTED,
                    EnumSet.of(EngagementState.CLIENT_DECISION)),
            Map.entry(EngagementState.CLIENT_DECISION,
                    EnumSet.of(EngagementState.REVIEW)),
            Map.entry(EngagementState.REVIEW,
                    EnumSet.of(EngagementState.COMPLETED))
    );

    private EngagementPolicy() {}

    public static void assertValidTransition(EngagementState from, EngagementState to) {
        Set<EngagementState> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(EngagementState.class));
        if (!allowed.contains(to)) {
            throw new InvalidTransitionException(from, to);
        }
    }

    public static boolean canTransitionTo(EngagementState from, EngagementState to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(EngagementState.class)).contains(to);
    }
}
