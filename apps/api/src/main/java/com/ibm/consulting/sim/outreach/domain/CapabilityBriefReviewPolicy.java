package com.ibm.consulting.sim.outreach.domain;

/**
 * A stable scoring rubric for a requested brief. This chooses the engagement
 * consequence; an AI layer may enrich language later but cannot alter it.
 */
public final class CapabilityBriefReviewPolicy {

    private static final int MIN_SECTION_LENGTH = 80;

    private CapabilityBriefReviewPolicy() {}

    public static CapabilityBriefReview evaluate(String relevantExperience, String approach,
                                                  String caseExample, String clientFit) {
        int experience = completenessScore(relevantExperience);
        int approachScore = completenessScore(approach);
        int example = completenessScore(caseExample);
        int fit = completenessScore(clientFit);
        int totalLength = relevantExperience.length() + approach.length() + caseExample.length() + clientFit.length();

        int industryRelevance = average(experience, example);
        int evidenceQuality = average(experience, example, fit);
        int clarity = average(approachScore, fit);
        int credibility = average(experience, example, approachScore);
        boolean accepted = experience >= 70 && approachScore >= 70 && example >= 70 && fit >= 70 && totalLength >= 500;

        if (accepted) {
            return new CapabilityBriefReview(
                    OutreachOutcome.ACCEPTED,
                    "Thank you. This brief addresses the experience, delivery approach and risk controls we asked for. Let's schedule a short discovery call to discuss the fit in more detail.",
                    fit, industryRelevance, evidenceQuality, clarity, credibility);
        }

        return new CapabilityBriefReview(
                OutreachOutcome.FOLLOW_UP_REQUIRED,
                "Thank you for the summary. Before we reconnect, please strengthen the missing sections with specific, relevant evidence and explain how your approach fits our operating constraints.",
                fit, industryRelevance, evidenceQuality, clarity, credibility);
    }

    private static int completenessScore(String value) {
        int length = value == null ? 0 : value.trim().length();
        if (length >= 180) return 90;
        if (length >= MIN_SECTION_LENGTH) return 75;
        if (length >= 40) return 50;
        return 20;
    }

    private static int average(int... values) {
        int total = 0;
        for (int value : values) total += value;
        return total / values.length;
    }
}
