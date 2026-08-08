package com.ibm.consulting.sim.meeting.domain;

import java.util.Optional;
import java.util.UUID;

public interface PersonaStateRepository {
    PersonaState save(PersonaState state);
    Optional<PersonaState> findByEngagementId(UUID engagementId);
}
