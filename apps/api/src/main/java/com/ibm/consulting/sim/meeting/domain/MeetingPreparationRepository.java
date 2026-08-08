package com.ibm.consulting.sim.meeting.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeetingPreparationRepository {
    MeetingPreparation save(MeetingPreparation preparation);
    Optional<MeetingPreparation> findByEngagementId(UUID engagementId);
}
