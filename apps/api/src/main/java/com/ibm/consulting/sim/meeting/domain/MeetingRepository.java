package com.ibm.consulting.sim.meeting.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository {
    Meeting save(Meeting meeting);
    Optional<Meeting> findById(UUID id);
    Optional<Meeting> findByEngagementId(UUID engagementId);
}
