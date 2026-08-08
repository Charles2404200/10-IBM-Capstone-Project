package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.meeting.domain.MeetingPreparation;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataMeetingPreparationRepository extends JpaRepository<MeetingPreparation, UUID> {
    Optional<MeetingPreparation> findByEngagementId(UUID engagementId);
}

@Repository
class JpaMeetingPreparationRepository implements MeetingPreparationRepository {

    private final SpringDataMeetingPreparationRepository repo;

    JpaMeetingPreparationRepository(SpringDataMeetingPreparationRepository repo) {
        this.repo = repo;
    }

    @Override public MeetingPreparation save(MeetingPreparation preparation) { return repo.save(preparation); }
    @Override public Optional<MeetingPreparation> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
}
