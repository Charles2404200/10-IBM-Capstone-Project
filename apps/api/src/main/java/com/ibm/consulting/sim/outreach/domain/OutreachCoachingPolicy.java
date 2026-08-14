package com.ibm.consulting.sim.outreach.domain;

/** Concise, deterministic next-step coaching kept consistent with scored behaviour. */
public final class OutreachCoachingPolicy {
    private OutreachCoachingPolicy() { }

    public static String hintFor(OutreachAttempt attempt) {
        if (attempt.getOutcome() == OutreachOutcome.REJECTED) {
            return "Use professional, client-focused language. An insulting or abusive message ends the outreach attempt.";
        }
        if (score(attempt.getScorePersonalisation()) < 55) {
            return "Name the stakeholder or organisation and connect the note to their situation.";
        }
        if (score(attempt.getScoreRelevance()) < 55) {
            return "Use one concrete signal from your research instead of a general capability statement.";
        }
        if (score(attempt.getScoreCallToAction()) < 55) {
            return "End with one low-friction ask: suggest a short conversation with a time window.";
        }
        if (score(attempt.getScoreClarity()) < 55) {
            return "Tighten the message to a few short sentences so a busy stakeholder can scan it.";
        }
        return "Your message is well structured. Respond directly to the client’s latest constraint in the next turn.";
    }

    private static int score(Integer value) { return value == null ? 0 : value; }
}
