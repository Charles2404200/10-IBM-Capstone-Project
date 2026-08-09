package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.meeting.domain.MeetingResponseOptionSet;
import com.ibm.consulting.sim.meeting.domain.MeetingResponseOptionSetRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataMeetingResponseOptionSetRepository extends JpaRepository<MeetingResponseOptionSet, UUID> {
    Optional<MeetingResponseOptionSet> findByMeetingIdAndSourceSequence(UUID meetingId, int sourceSequence);
}

@Repository
class JpaMeetingResponseOptionSetRepository implements MeetingResponseOptionSetRepository {

    private final SpringDataMeetingResponseOptionSetRepository repository;

    JpaMeetingResponseOptionSetRepository(SpringDataMeetingResponseOptionSetRepository repository) {
        this.repository = repository;
    }

    @Override
    public MeetingResponseOptionSet save(MeetingResponseOptionSet optionSet) {
        return repository.save(optionSet);
    }

    @Override
    public Optional<MeetingResponseOptionSet> findByMeetingIdAndSourceSequence(UUID meetingId, int sourceSequence) {
        return repository.findByMeetingIdAndSourceSequence(meetingId, sourceSequence);
    }
}
