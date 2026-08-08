package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataPersonaStateRepository extends JpaRepository<PersonaState, UUID> {
    Optional<PersonaState> findByEngagementId(UUID engagementId);
}

@Repository
class JpaPersonaStateRepository implements PersonaStateRepository {

    private final SpringDataPersonaStateRepository repo;

    JpaPersonaStateRepository(SpringDataPersonaStateRepository repo) {
        this.repo = repo;
    }

    @Override public PersonaState save(PersonaState state) { return repo.save(state); }
    @Override public Optional<PersonaState> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
}
