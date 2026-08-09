package com.ibm.consulting.sim.meeting.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeetingResponseOptionSetRepository {
    MeetingResponseOptionSet save(MeetingResponseOptionSet optionSet);
    Optional<MeetingResponseOptionSet> findByMeetingIdAndSourceSequence(UUID meetingId, int sourceSequence);
}
