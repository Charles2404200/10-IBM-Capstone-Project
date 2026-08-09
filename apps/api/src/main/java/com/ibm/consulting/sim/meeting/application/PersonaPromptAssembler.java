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
                 "safety": {"allowed": boolean, "reason": string|null}}
                """.formatted(
                persona.getName(), persona.getJobTitle(), persona.getOrganisation(),
                persona.getCommunicationStyle(), persona.getBusinessGoals(), persona.getVisibleConcerns(),
                persona.getHiddenConcerns(),
                state.getTrust(), state.getInterest(), state.getPatience(), behaviourControls(difficultyProfile),
                state.getDisclosedFacts().isEmpty() ? "(none)" : String.join(", ", state.getDisclosedFacts()),
                evidenceSummary, knowledgeSummary, transcript, learnerMessage);
    }

    private static String behaviourControls(DifficultyProfile profile) {
        String scoringInstruction = "Do not reward greetings, vague prompts, or requests for the client to do the consultant's discovery. "
                + "First assess whether the consultant directly answered the latest client concern. Use negative stateDelta for vague, evasive, dismissive, or unprofessional behaviour. "
                + "detectedLearnerBehaviours must contain only observed labels from: directly_addresses_concern, acknowledges_constraint, uses_client_fact, uses_disclosed_evidence, quantifies_business_impact, uses_specific_metric, asks_focused_question, grounded_recommendation, evasive, unprepared, dismissive, does_not_answer, unsupported_claim. "
                + "Only include a positive label when the learner's actual message demonstrates it; omit labels for a greeting or unsupported generic statement.";
        if (profile == null) return "Use the scenario's normal level of specificity and challenge. " + scoringInstruction;
        return "Resistance %d/100. The client needs a credible next step within %d simulated days. "
                + "Ask for more precise evidence when resistance is high. "
                + "Do not disclose hidden or unvalidated facts, and never decide simulation outcomes. %s"
                .formatted(profile.personaResistance(), profile.timelinePressureDays(), scoringInstruction);
    }
}
