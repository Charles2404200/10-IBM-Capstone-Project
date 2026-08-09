package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingSafetyPolicyTest {

    @Test
    void endsMeetingForExplicitProfanity() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());

        var decision = MeetingSafetyPolicy.evaluate("Who tf do you think you are?", state);

        assertTrue(decision.isPresent());
        assertEquals(MeetingTerminationReason.UNPROFESSIONAL_CONDUCT, decision.get().reason());
    }

    @Test
    void endsMeetingWhenAnyRelationshipDimensionFallsBelowMinimum() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(-16, 0, 0));

        var decision = MeetingSafetyPolicy.evaluate("Could you clarify the implementation constraint?", state, 6);

        assertTrue(decision.isPresent());
        assertEquals(MeetingTerminationReason.RELATIONSHIP_THRESHOLD_BREACH, decision.get().reason());
    }

    @Test
    void doesNotTreatDirectButProfessionalLanguageAsAConductFailure() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());

        assertFalse(MeetingSafetyPolicy.evaluate(
                "I disagree with that assumption. Could we validate it with the operations team?", state, 1).isPresent());
    }
}
