package com.ibm.consulting.sim.outreach.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityBriefReviewPolicyTest {

    @Test
    void acceptsACompleteCapabilityBrief() {
        String detailedSection = "We have led multiple pharmaceutical distribution transformations with phased delivery, "
                + "warehouse integration and explicit operating controls, producing measurable outcomes for comparable clients.";

        CapabilityBriefReview review = CapabilityBriefReviewPolicy.evaluate(
                detailedSection, detailedSection, detailedSection, detailedSection);

        assertThat(review.outcome()).isEqualTo(OutreachOutcome.ACCEPTED);
        assertThat(review.clientFit()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void requiresFollowUpWhenTheBriefIsTooThin() {
        CapabilityBriefReview review = CapabilityBriefReviewPolicy.evaluate("Short", "Short", "Short", "Short");

        assertThat(review.outcome()).isEqualTo(OutreachOutcome.FOLLOW_UP_REQUIRED);
    }
}
