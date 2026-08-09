package com.ibm.consulting.sim.meeting.application;

import java.util.List;

/** Schema-validated AI output used to create a learner choice set. */
public record GuidedResponseOptions(List<String> options) {
    public GuidedResponseOptions {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
