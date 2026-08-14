package com.ibm.consulting.sim.meeting.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository {
    Meeting save(Meeting meeting);
    Optional<Meeting> findById(UUID id);
    List<Meeting> findAllByEngagementIdOrderByCreatedAtAsc(UUID engagementId);
    List<Meeting> findAllByEngagementIdIn(List<UUID> engagementIds);
    /** Returns the latest attempt for the engagement. */
    Optional<Meeting> findByEngagementId(UUID engagementId);
}
