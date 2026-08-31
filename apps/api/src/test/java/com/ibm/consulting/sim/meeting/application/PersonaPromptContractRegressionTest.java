package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the non-negotiable safety and closure instructions in the persona prompt. */
@Tag("ai-evaluation")
class PersonaPromptContractRegressionTest {

    @Test
    void keepsScoringGroundingAndFinalTurnContractsInThePersonaPrompt() {
        PersonaProfile persona = new PersonaProfile();
        persona.setId(UUID.randomUUID());
        persona.setName("Elena Torres");
        persona.setJobTitle("VP Network Operations");
        persona.setOrganisation("HarborGrid Utilities");
        persona.setCommunicationStyle("Direct and risk-conscious");
        persona.setVisibleConcerns("Operational resilience and safety");
        persona.setHiddenConcerns("Avoid unvalidated commitments");
        persona.setBusinessGoals("Improve outage response");

        String prompt = PersonaPromptAssembler.assemble(persona, PersonaState.initial(UUID.randomUUID()),
                List.of(), List.of(), List.of(), "Hello", DifficultyProfile.defaults(3, 3, 3, 3), true);

        assertThat(prompt)
                .contains("Do not reward greetings, vague prompts")
                .contains("Only include a positive label when the learner's actual message demonstrates it")
                .contains("Do not disclose hidden or unvalidated facts")
                .contains("never decide simulation outcomes")
                .contains("Do not raise another question, objection, requirement, or discovery thread")
                .contains("client_ready_to_close and client_committed_next_step")
                .contains("guidedResponseOptions must be an empty array");
    }
}
