package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataMeetingRepository extends JpaRepository<Meeting, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from Meeting meeting where meeting.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") UUID id);

    List<Meeting> findAllByEngagementIdOrderByCreatedAtAsc(UUID engagementId);
    List<Meeting> findByEngagementIdIn(List<UUID> engagementIds);
    Optional<Meeting> findFirstByEngagementIdOrderByCreatedAtDesc(UUID engagementId);
}

@Repository
class JpaMeetingRepository implements MeetingRepository {

    private final SpringDataMeetingRepository repo;

    JpaMeetingRepository(SpringDataMeetingRepository repo) {
        this.repo = repo;
    }

    @Override public Meeting save(Meeting meeting) { return repo.save(meeting); }
    @Override public Optional<Meeting> findById(UUID id) { return repo.findById(id); }
    @Override public Optional<Meeting> findByIdForUpdate(UUID id) { return repo.findByIdForUpdate(id); }
    @Override public List<Meeting> findAllByEngagementIdOrderByCreatedAtAsc(UUID engagementId) {
        return repo.findAllByEngagementIdOrderByCreatedAtAsc(engagementId);
    }
    @Override public List<Meeting> findAllByEngagementIdIn(List<UUID> engagementIds) {
        return engagementIds.isEmpty() ? List.of() : repo.findByEngagementIdIn(engagementIds);
    }
    @Override public Optional<Meeting> findByEngagementId(UUID engagementId) {
        return repo.findFirstByEngagementIdOrderByCreatedAtDesc(engagementId);
    }
}
