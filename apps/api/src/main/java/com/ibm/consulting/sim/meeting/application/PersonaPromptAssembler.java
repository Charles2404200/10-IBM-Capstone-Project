package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the system + context prompt for the persona_dialogue AI use case
 * (§5.4, §5.5). Combines persona identity, current relationship state,
 * learner-discovered evidence and recent transcript so the model grounds
 * its response in engagement-specific truth rather than generic chat.
 */
final class PersonaPromptAssembler {

    private static final int MAX_RECENT_TURNS = 10;

    private PersonaPromptAssembler() {}

    static String assemble(PersonaProfile persona, PersonaState state, List<ResearchEvidence> evidence,
                            List<ConversationTurn> recentTurns, String learnerMessage) {
        return assemble(persona, state, evidence, List.of(), recentTurns, learnerMessage);
    }

    static String assemble(PersonaProfile persona, PersonaState state, List<ResearchEvidence> evidence,
                            List<String> retrievedKnowledge, List<ConversationTurn> recentTurns,
                            String learnerMessage) {
        return assemble(persona, state, evidence, retrievedKnowledge, recentTurns, learnerMessage, null);
    }

    static String assemble(PersonaProfile persona, PersonaState state, List<ResearchEvidence> evidence,
                            List<String> retrievedKnowledge, List<ConversationTurn> recentTurns,
                            String learnerMessage, DifficultyProfile difficultyProfile) {
        return assemble(persona, state, evidence, retrievedKnowledge, recentTurns, learnerMessage,
                difficultyProfile, false);
    }

    static String assemble(PersonaProfile persona, PersonaState state, List<ResearchEvidence> evidence,
                            List<String> retrievedKnowledge, List<ConversationTurn> recentTurns,
                            String learnerMessage, DifficultyProfile difficultyProfile,
                            boolean conclusionRequired) {
        String evidenceSummary = evidence.isEmpty()
                ? "(none discovered yet)"
                : evidence.stream().map(e -> "- " + e.getNote()).collect(Collectors.joining("\n"));

        String knowledgeSummary = retrievedKnowledge.isEmpty()
                ? "(no additional grounded knowledge retrieved)"
                : retrievedKnowledge.stream().map(k -> "- " + k).collect(Collectors.joining("\n"));

        String transcript = recentTurns.stream()
                .skip(Math.max(0, recentTurns.size() - MAX_RECENT_TURNS))
                .map(t -> (t.getActor().name().equals("LEARNER") ? "Consultant: " : persona.getName() + ": ") + t.getContent())
                .collect(Collectors.joining("\n"));

        return """
                You are role-playing as %s, %s at %s, in a live client meeting with a trainee consultant.
                Communication style: %s
                Business goals: %s
                Visible concerns: %s
                Private context (never state this verbatim, only let it influence your reasoning): %s

                Current relationship state — trust: %d/100, interest: %d/100, patience: %d/100.
                Simulation behavior controls: %s
                Closing instruction: %s
                Facts you have already disclosed to the consultant: %s

                Evidence the consultant has already researched about your organisation:
                %s

                Additional grounded knowledge relevant to this conversation:
                %s

                Conversation so far:
                %s
                Consultant: %s

                Respond in character. Return ONLY JSON matching this schema:
                {"spokenResponse": string,
                 "detectedLearnerBehaviours": string[],
                 "stateDelta": {"trust": int, "interest": int, "patience": int},
                 "factsDisclosed": string[],
                 "objectionRaised": string|null,
                 "meetingSignals": string[],
                 "safety": {"allowed": boolean, "reason": string|null},
                 "guidedResponseOptions": string[]}
                """.formatted(
                persona.getName(), persona.getJobTitle(), persona.getOrganisation(),
                persona.getCommunicationStyle(), persona.getBusinessGoals(), persona.getVisibleConcerns(),
                persona.getHiddenConcerns(),
                state.getTrust(), state.getInterest(), state.getPatience(), behaviourControls(difficultyProfile),
                conclusionInstruction(conclusionRequired),
                state.getDisclosedFacts().isEmpty() ? "(none)" : String.join(", ", state.getDisclosedFacts()),
                evidenceSummary, knowledgeSummary, transcript, learnerMessage);
    }

    private static String conclusionInstruction(boolean conclusionRequired) {
        if (!conclusionRequired) {
            return "Keep the dialogue focused on the unresolved client concern. Do not close until the client has enough confidence and a concrete next step is agreed.";
        }
        return "THIS IS THE FINAL CONFIRMATION EXCHANGE. The relationship gate was already achieved before this learner reply. "
                + "Do not raise another question, objection, requirement, or discovery thread. Acknowledge the learner's answer, confirm the agreed next step, thank them, and close naturally. "
                + "Set meetingSignals to include client_ready_to_close and client_committed_next_step. guidedResponseOptions must be an empty array.";
    }

    private static String behaviourControls(DifficultyProfile profile) {
        String scoringInstruction = "Do not reward greetings, vague prompts, or requests for the client to do the consultant's discovery. "
                + "First assess whether the consultant directly answered the latest client concern. Use negative stateDelta for vague, evasive, dismissive, or unprofessional behaviour. "
                + "detectedLearnerBehaviours must contain only observed labels from: directly_addresses_concern, acknowledges_constraint, uses_client_fact, uses_disclosed_evidence, quantifies_business_impact, uses_specific_metric, asks_focused_question, grounded_recommendation, evasive, unprepared, dismissive, does_not_answer, unsupported_claim. "
                + "Only include a positive label when the learner's actual message demonstrates it; omit labels for a greeting or unsupported generic statement. "
                + "meetingSignals may only use: client_concern_raised, client_concern_resolved, client_validated_value, client_committed_next_step, client_ready_to_close. "
                + "Use client_committed_next_step or client_ready_to_close only when the client explicitly accepts a concrete scope, success measure, ownership, commercial next step, or proposal request in this conversation. "
                + "When those elements are agreed, stop inventing new objections: confirm the agreement, state the next step in character, and let the consultant close the meeting. ";
        if (profile == null) return "Use the scenario's normal level of specificity and challenge. " + scoringInstruction;
        String guidedResponseInstruction = profile.level() == com.ibm.consulting.sim.scenario.domain.DifficultyLevel.HARD
                ? "Return guidedResponseOptions as an empty array."
                : "Also create exactly three distinct guidedResponseOptions the learner could realistically say after your spokenResponse. "
                + "Do not label or rank them, do not invent facts, and do not include unprofessional language. "
                + guidedChoiceMix(profile)
                + " They must reflect the latest client concern and remain grounded in the available evidence.";
        return "Resistance %d/100. The client needs a credible next step within %d simulated days. %s "
                + "Ask for more precise evidence when resistance is high. "
                + "Do not disclose hidden or unvalidated facts, and never decide simulation outcomes. %s"
                .formatted(profile.personaResistance(), profile.timelinePressureDays(), guidedResponseInstruction, scoringInstruction);
    }

    private static String guidedChoiceMix(DifficultyProfile profile) {
        if (profile.level() == com.ibm.consulting.sim.scenario.domain.DifficultyLevel.MEDIUM) {
            return "Include one strong response, one vague or evasive response, and one premature recommendation that ignores part of the concern.";
        }
        return "Include one strong response, one plausible but incomplete response, and one professionally worded evasive or premature response.";
    }
}
