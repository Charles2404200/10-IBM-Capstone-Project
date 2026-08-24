package com.ibm.consulting.sim.ai.domain;

import java.util.List;

/** Structured, evidence-grounded coaching narrative for the assessment_feedback use case (§5.1). */
public record AssessmentFeedback(String feedbackSummary, List<String> strengths, List<String> improvementAreas) {

    public static AssessmentFeedback pending(int overallScore) {
        return new AssessmentFeedback(
                "Your deterministic assessment is ready at %d/100. Personalised AI coaching is being prepared."
                        .formatted(overallScore),
                List.of(),
                List.of());
    }

    public static AssessmentFeedback safeFallback(int overallScore) {
        return new AssessmentFeedback(
                "You completed the engagement with an overall competency score of %d/100. Detailed AI coaching is temporarily unavailable; review your competency breakdown below."
                        .formatted(overallScore),
                List.of(),
                List.of("Review the competency breakdown for areas to focus on next time."));
    }
}
