package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.PersonaState;

import java.util.Set;
import java.util.UUID;

public record PersonaStateResponse(UUID engagementId, int trust, int interest, int patience, Set<String> disclosedFacts) {
    public static PersonaStateResponse from(PersonaState s) {
        // Materialize eagerly (Set.copyOf, not a view) — this record is serialized
        // to JSON by the SSE controller *after* the transactional method that
        // built it has already returned and closed the Hibernate session, so a
        // still-lazy collection view would throw LazyInitializationException.
        return new PersonaStateResponse(s.getEngagementId(), s.getTrust(), s.getInterest(), s.getPatience(),
                Set.copyOf(s.getDisclosedFacts()));
    }
}
