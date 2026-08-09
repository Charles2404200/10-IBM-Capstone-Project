package com.ibm.consulting.sim.outreach.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachRequestPolicyTest {

    @Test
    void mapsAConciseExperienceSummaryRequestToCapabilityBrief() {
        String reply = "A concise summary of your experience in pharmaceutical distribution, particularly around phased "
                + "implementation and integration with our existing WMS, would be helpful. Please send it over.";

        OutreachRequestDetails details = OutreachRequestPolicy.detailsFor(
                OutreachOutcome.FOLLOW_UP_REQUIRED, reply, null);

        assertThat(details.nextAction()).isEqualTo(OutreachNextAction.SUBMIT_CAPABILITY_BRIEF);
        assertThat(details.requirements()).contains(
                "Relevant pharmaceutical distribution experience",
                "Phased implementation approach",
                "Integration with existing WMS");
    }

    @Test
    void keepsAcceptedMeetingSeparateFromDocumentWorkflow() {
        assertThat(OutreachRequestPolicy.nextActionFor(
                OutreachOutcome.ACCEPTED, "Let's schedule time next week."))
                .isEqualTo(OutreachNextAction.CONTINUE_TO_MEETING);
    }

    @Test
    void upgradesLegacyFollowUpWhenTheClientActuallyRequestsAConcreteBrief() {
        String reply = "Could you perhaps send over a brief, more concrete example of similar work, the phased "
                + "approach, and the measurable value it delivered?";

        OutreachRequestDetails details = OutreachRequestPolicy.detailsFor(
                OutreachOutcome.FOLLOW_UP_REQUIRED, reply, OutreachNextAction.SEND_FOLLOW_UP);

        assertThat(details.nextAction()).isEqualTo(OutreachNextAction.SUBMIT_CAPABILITY_BRIEF);
        assertThat(details.requirements()).contains(
                "Phased implementation approach",
                "A concrete example of comparable work",
                "Measurable client outcomes from comparable work");
    }
}
