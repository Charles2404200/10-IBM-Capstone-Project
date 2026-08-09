package com.ibm.consulting.sim.meeting.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Non-AI meeting termination guard. It keeps professionalism and minimum
 * relationship standards deterministic, auditable and consistent for every
 * learner regardless of the AI provider used for persona dialogue.
 */
public final class MeetingSafetyPolicy {

    public static final int MINIMUM_RELATIONSHIP_SCORE = 35;
    private static final int MINIMUM_TURNS_BEFORE_RELATIONSHIP_FAILURE = 6;

    private static final Pattern EXPLICIT_PROFANITY = Pattern.compile(
            "\\b(fuck(?:ing)?|shit|bitch|asshole|wtf|tf)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_INSULT = Pattern.compile(
            "\\b(you('re| are)?\\s+(an?\\s+)?(idiot|moron|stupid))\\b", Pattern.CASE_INSENSITIVE);

    private MeetingSafetyPolicy() {
    }

    public static Optional<MeetingTerminationDecision> evaluate(String learnerMessage, PersonaState state) {
        return evaluate(learnerMessage, state, MINIMUM_TURNS_BEFORE_RELATIONSHIP_FAILURE);
    }

    public static Optional<MeetingTerminationDecision> evaluate(String learnerMessage, PersonaState state,
                                                                  int learnerTurnNumber) {
        if (containsUnprofessionalLanguage(learnerMessage)) {
            return Optional.of(new MeetingTerminationDecision(
                    MeetingTerminationReason.UNPROFESSIONAL_CONDUCT,
                    "The client ended the meeting because your language was unprofessional. "
                            + "Professional communication is a non-negotiable client-facing standard.",
                    List.of(
                            "Acknowledge pressure or disagreement without personal language.",
                            "Use a neutral, specific question to move the conversation forward.",
                            "Ground recommendations in the client facts already disclosed.")));
        }

        if (learnerTurnNumber >= MINIMUM_TURNS_BEFORE_RELATIONSHIP_FAILURE
                && (state.getTrust() < MINIMUM_RELATIONSHIP_SCORE
                || state.getInterest() < MINIMUM_RELATIONSHIP_SCORE
                || state.getPatience() < MINIMUM_RELATIONSHIP_SCORE)) {
            return Optional.of(new MeetingTerminationDecision(
                    MeetingTerminationReason.RELATIONSHIP_THRESHOLD_BREACH,
                    "The meeting ended because the client relationship fell below the minimum operating threshold. "
                            + relationshipSummary(state),
                    List.of(
                            "Respond directly to the client's latest concern before introducing a new idea.",
                            "Use one concrete fact the client has shared and ask one focused follow-up question.",
                            "Keep the next response concise while the client's patience is limited.")));
        }

        return Optional.empty();
    }

    static boolean containsUnprofessionalLanguage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT)
                .replace('@', 'a')
                .replace('$', 's')
                .replace('0', 'o')
                .replace('1', 'i');
        return EXPLICIT_PROFANITY.matcher(normalized).find() || DIRECT_INSULT.matcher(normalized).find();
    }

    private static String relationshipSummary(PersonaState state) {
        return "Trust %d/%d, Interest %d/%d, Patience %d/%d."
                .formatted(
                        state.getTrust(), MINIMUM_RELATIONSHIP_SCORE,
                        state.getInterest(), MINIMUM_RELATIONSHIP_SCORE,
                        state.getPatience(), MINIMUM_RELATIONSHIP_SCORE);
    }
}
