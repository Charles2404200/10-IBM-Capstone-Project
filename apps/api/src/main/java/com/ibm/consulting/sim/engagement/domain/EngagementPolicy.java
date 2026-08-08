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
            Map.entry(EngagementState.DRAFT,
                    EnumSet.of(EngagementState.LEAD_SELECTED)),
            Map.entry(EngagementState.LEAD_SELECTED,
                    EnumSet.of(EngagementState.RESEARCH_COMPLETED)),
            Map.entry(EngagementState.RESEARCH_COMPLETED,
                    EnumSet.of(EngagementState.OUTREACH_IN_PROGRESS)),
            Map.entry(EngagementState.OUTREACH_IN_PROGRESS,
                    EnumSet.of(EngagementState.MEETING_SECURED, EngagementState.OUTREACH_FAILED)),
            Map.entry(EngagementState.OUTREACH_FAILED,
                    EnumSet.of(EngagementState.OUTREACH_IN_PROGRESS)),
            Map.entry(EngagementState.MEETING_SECURED,
                    EnumSet.of(EngagementState.PREPARATION_COMPLETED)),
            Map.entry(EngagementState.PREPARATION_COMPLETED,
                    EnumSet.of(EngagementState.MEETING_IN_PROGRESS)),
            Map.entry(EngagementState.MEETING_IN_PROGRESS,
                    EnumSet.of(EngagementState.MEETING_COMPLETED)),
            Map.entry(EngagementState.MEETING_COMPLETED,
                    EnumSet.of(EngagementState.PROPOSAL_SUBMITTED)),
            Map.entry(EngagementState.PROPOSAL_SUBMITTED,
                    EnumSet.of(EngagementState.CONTRACT_WON, EngagementState.CONTRACT_LOST)),
            Map.entry(EngagementState.CONTRACT_WON,
                    EnumSet.of(EngagementState.REVIEW_AVAILABLE)),
            Map.entry(EngagementState.CONTRACT_LOST,
                    EnumSet.of(EngagementState.REVIEW_AVAILABLE)),
            Map.entry(EngagementState.REVIEW_AVAILABLE,
                    EnumSet.of(EngagementState.ARCHIVED))
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
