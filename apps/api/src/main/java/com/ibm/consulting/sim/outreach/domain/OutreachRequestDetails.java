package com.ibm.consulting.sim.outreach.domain;

import java.util.List;

/** A client-request projection derived from the latest canonical reply. */
public record OutreachRequestDetails(
        OutreachNextAction nextAction,
        String title,
        String summary,
        List<String> requirements) {

    public static OutreachRequestDetails none() {
        return new OutreachRequestDetails(OutreachNextAction.NONE, null, null, List.of());
    }
}
