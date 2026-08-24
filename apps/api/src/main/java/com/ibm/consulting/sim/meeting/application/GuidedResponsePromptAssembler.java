package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.List;
import java.util.stream.Collectors;

/** Prompt construction stays separate from application orchestration and persistence. */
final class GuidedResponsePromptAssembler {

    private GuidedResponsePromptAssembler() {}

    static String assemble(PersonaProfile persona, PersonaState state, List<ResearchEvidence> evidence,
                           List<String> retrievedKnowledge, List<ConversationTurn> recentTurns,
                           DifficultyProfile profile) {
        String transcript = recentTurns.stream()
                .skip(Math.max(0, recentTurns.size() - 8))
                .map(turn -> (turn.getActor().name().equals("LEARNER") ? "Consultant: " : persona.getName() + ": ")
                        + turn.getContent())
                .collect(Collectors.joining("\n"));
        String evidenceSummary = evidence.isEmpty() ? "(none discovered yet)" : evidence.stream()
                .map(item -> "- " + item.getNote()).collect(Collectors.joining("\n"));
        String knowledgeSummary = retrievedKnowledge.isEmpty() ? "(none)" : retrievedKnowledge.stream()
                .map(item -> "- " + item).collect(Collectors.joining("\n"));

        return """
                You are a consulting-skills coach preparing the next turn in a controlled client simulation.
                The learner is speaking with %s, %s at %s.
                Client communication style: %s
                Client goals: %s
                Client concerns: %s
                Current relationship state: trust=%d, interest=%d, patience=%d.

                Evidence already discovered by the learner:
                %s

                Grounded scenario knowledge:
                %s

                Conversation so far:
                %s

                Return ONLY valid JSON in this exact schema:
                {"options":[string,string,string]}

                Create exactly three distinct, concise responses the learner could genuinely say next.
                Do not label, rank, explain, or reveal which option is best. Never invent facts, budgets, case studies,
                commitments, or outcomes. Keep each option professional and grounded in the latest client concern.
                %s
                Do not produce greetings, insults, filler, or meta-commentary.
                """.formatted(persona.getName(), persona.getJobTitle(), persona.getOrganisation(),
                persona.getCommunicationStyle(), persona.getBusinessGoals(), persona.getVisibleConcerns(),
                state.getTrust(), state.getInterest(), state.getPatience(), evidenceSummary, knowledgeSummary, transcript,
                choiceMix(profile));
    }

    private static String choiceMix(DifficultyProfile profile) {
        if (profile.level() == DifficultyLevel.MEDIUM) {
            return "Option 1 must directly advance the client's concern. Options 2 and 3 must be plausible but "
                    + "counterproductive consulting choices: one should be vague or evasive, and one should jump to "
                    + "a recommendation before addressing the concern. They must remain professional.";
        }
        return "Option 1 must directly advance the client's concern. Option 2 may be incomplete but plausible. "
                + "Option 3 must be a professionally worded evasive or premature response that would weaken the conversation.";
    }
}
