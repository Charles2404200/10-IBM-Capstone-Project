package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.MeetingPreparation;

import java.util.List;
import java.util.UUID;

public record MeetingPreparationResponse(
        UUID id,
        UUID engagementId,
        String objective,
        List<String> agenda,
        List<String> discoveryQuestions,
        int readinessScore,
        boolean ready) {

    public static MeetingPreparationResponse from(MeetingPreparation p) {
        return new MeetingPreparationResponse(p.getId(), p.getEngagementId(), p.getObjective(),
                p.getAgenda(), p.getDiscoveryQuestions(), p.getReadinessScore(), p.isReady());
    }
}
