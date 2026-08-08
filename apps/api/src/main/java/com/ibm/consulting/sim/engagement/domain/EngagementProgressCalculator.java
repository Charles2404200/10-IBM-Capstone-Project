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
        REVIEW("AI Review");

        private final String label;

        Phase(String label) { this.label = label; }

        public String label() { return label; }
    }

    private static final int TOTAL_STEPS = 10;

    public static Phase phaseOf(EngagementState state) {
        return switch (state) {
            case DRAFT -> Phase.LEAD;
            case LEAD_SELECTED, RESEARCH_COMPLETED -> Phase.CLIENT_INTELLIGENCE;
            case OUTREACH_IN_PROGRESS, OUTREACH_FAILED -> Phase.OUTREACH;
            case MEETING_SECURED, PREPARATION_COMPLETED -> Phase.MEETING_PREPARATION;
            case MEETING_IN_PROGRESS, MEETING_COMPLETED -> Phase.LIVE_MEETING;
            case PROPOSAL_SUBMITTED -> Phase.PROPOSAL;
            case CONTRACT_WON, CONTRACT_LOST -> Phase.OUTCOME;
            case REVIEW_AVAILABLE, ARCHIVED -> Phase.REVIEW;
        };
    }

    /** Ordinal step (0-based) within the fixed 10-step lifecycle, used to derive the progress bar. */
    private static int stepOf(EngagementState state) {
        return switch (state) {
            case DRAFT -> 0;
            case LEAD_SELECTED -> 1;
            case RESEARCH_COMPLETED -> 2;
            case OUTREACH_IN_PROGRESS, OUTREACH_FAILED -> 3;
            case MEETING_SECURED -> 4;
            case PREPARATION_COMPLETED -> 5;
            case MEETING_IN_PROGRESS -> 6;
            case MEETING_COMPLETED -> 7;
            case PROPOSAL_SUBMITTED -> 8;
            case CONTRACT_WON, CONTRACT_LOST -> 9;
            case REVIEW_AVAILABLE, ARCHIVED -> 10;
        };
    }

    public static int progressPercent(EngagementState state) {
        return Math.min(100, Math.round((stepOf(state) * 100f) / TOTAL_STEPS));
    }

    /** A short, concrete instruction telling the learner exactly what to do next. */
    public static String nextAction(EngagementState state) {
        return switch (state) {
            case DRAFT -> "Select a lead to begin the engagement";
            case LEAD_SELECTED -> "Research the client in Client Intelligence";
            case RESEARCH_COMPLETED -> "Begin outreach to secure a meeting";
            case OUTREACH_IN_PROGRESS -> "Follow up on outreach";
            case OUTREACH_FAILED -> "Revise your outreach approach and retry";
            case MEETING_SECURED -> "Prepare for the client meeting";
            case PREPARATION_COMPLETED -> "Start the live meeting";
            case MEETING_IN_PROGRESS -> "Continue the conversation with the client";
            case MEETING_COMPLETED -> "Draft your proposal";
            case PROPOSAL_SUBMITTED -> "Awaiting client decision";
            case CONTRACT_WON -> "Review your engagement outcome";
            case CONTRACT_LOST -> "Review what happened and learn for next time";
            case REVIEW_AVAILABLE -> "View your AI-generated performance review";
            case ARCHIVED -> "Engagement complete";
        };
    }
}
