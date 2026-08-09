package com.ibm.consulting.sim.meeting.application;

import java.util.List;

/** AI-authored coaching only; it cannot alter the deterministic completion result. */
public record MeetingDebriefNarrative(String feedback, List<String> tips) {
    public static MeetingDebriefNarrative fallback(boolean passed, List<String> unmetRequirements) {
        String feedback = passed
                ? "You built sufficient trust, interest and patience to move the client conversation forward."
                : "The meeting did not meet the relationship threshold required to progress.";
        List<String> tips = passed
                ? List.of("Carry the confirmed priorities into your discovery synthesis.")
                : unmetRequirements.stream()
                        .map(requirement -> "Strengthen " + requirement.split(" ")[0].toLowerCase() + " before ending the meeting.")
                        .toList();
        return new MeetingDebriefNarrative(feedback, tips);
    }
}
