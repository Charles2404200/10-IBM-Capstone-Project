package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataMeetingRepository extends JpaRepository<Meeting, UUID> {
    Optional<Meeting> findByEngagementId(UUID engagementId);
}

@Repository
class JpaMeetingRepository implements MeetingRepository {

    private final SpringDataMeetingRepository repo;

    JpaMeetingRepository(SpringDataMeetingRepository repo) {
        this.repo = repo;
    }

    @Override public Meeting save(Meeting meeting) { return repo.save(meeting); }
    @Override public Optional<Meeting> findById(UUID id) { return repo.findById(id); }
    @Override public Optional<Meeting> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
}
