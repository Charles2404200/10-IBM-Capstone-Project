package com.ibm.consulting.sim.engagement.domain;

/**
 * Maps the technical {@link EngagementState} machine onto learner-facing
 * "cockpit" concepts: a human phase label, a 0–100 progress percentage, and a
 * concrete next-action prompt. Pure, stateless (Policy/Engine pattern, matching
 * {@code EngagementPolicy}/{@code ReadinessPolicy}/{@code AssessmentEngine}).
 *
 * <p>The consulting lifecycle surfaced to the learner is a coarser grouping of
 * the 14 engagement states into 8 phases:
 * Lead → Client Intelligence → Outreach → Meeting Prep → Live Meeting →
 * Proposal → Outcome → Review.
 */
public final class EngagementProgressCalculator {

    private EngagementProgressCalculator() {}

    public enum Phase {
        LEAD("Lead Selection"),
        CLIENT_INTELLIGENCE("Client Intelligence"),
        OUTREACH("Outreach"),
        MEETING_PREPARATION("Meeting Preparation"),
        LIVE_MEETING("Live Meeting"),
        PROPOSAL("Proposal & Negotiation"),
        OUTCOME("Outcome"),
        REVIEW("AI Review"),
        COMPLETED("Completed");

        private final String label;

        Phase(String label) { this.label = label; }

        public String label() { return label; }
    }

    private static final int TOTAL_STEPS = 13;

    public static Phase phaseOf(EngagementState state) {
        return switch (state) {
            case QUALIFYING -> Phase.LEAD;
            case CLIENT_INTELLIGENCE, HYPOTHESIS_READY -> Phase.CLIENT_INTELLIGENCE;
            case OUTREACHING -> Phase.OUTREACH;
            case MEETING_SECURED, PREPARING -> Phase.MEETING_PREPARATION;
            case IN_MEETING -> Phase.LIVE_MEETING;
            case DISCOVERY_COMPLETE, PROPOSAL_DRAFT, PROPOSAL_SUBMITTED -> Phase.PROPOSAL;
            case CLIENT_DECISION -> Phase.OUTCOME;
            case REVIEW -> Phase.REVIEW;
            case COMPLETED -> Phase.COMPLETED;
        };
    }

    /** Ordinal step (0-based) within the fixed 10-step lifecycle, used to derive the progress bar. */
    private static int stepOf(EngagementState state) {
        return switch (state) {
            case QUALIFYING -> 0;
            case CLIENT_INTELLIGENCE -> 1;
            case HYPOTHESIS_READY -> 2;
            case OUTREACHING -> 3;
            case MEETING_SECURED -> 4;
            case PREPARING -> 5;
            case IN_MEETING -> 6;
            case DISCOVERY_COMPLETE -> 7;
            case PROPOSAL_DRAFT -> 8;
            case PROPOSAL_SUBMITTED -> 9;
            case CLIENT_DECISION -> 10;
            case REVIEW -> 12;
            case COMPLETED -> 13;
        };
    }

    public static int progressPercent(EngagementState state) {
        return Math.min(100, Math.round((stepOf(state) * 100f) / TOTAL_STEPS));
    }

    /** A short, concrete instruction telling the learner exactly what to do next. */
    public static String nextAction(EngagementState state) {
        return switch (state) {
            case QUALIFYING -> "Qualify and investigate a lead";
            case CLIENT_INTELLIGENCE -> "Build evidence and submit a grounded hypothesis";
            case HYPOTHESIS_READY -> "Choose an outreach strategy and secure a meeting";
            case OUTREACHING -> "Respond to outreach consequences";
            case MEETING_SECURED -> "Prepare for the client meeting";
            case PREPARING -> "Start the live meeting when readiness is high enough";
            case IN_MEETING -> "Run discovery and confirm the client situation";
            case DISCOVERY_COMPLETE -> "Synthesize discovery and begin the proposal";
            case PROPOSAL_DRAFT -> "Draft and validate your proposal";
            case PROPOSAL_SUBMITTED -> "Awaiting client decision";
            case CLIENT_DECISION -> "Review the client outcome";
            case REVIEW -> "View your AI-generated performance review";
            case COMPLETED -> "Engagement complete";
        };
    }
}
