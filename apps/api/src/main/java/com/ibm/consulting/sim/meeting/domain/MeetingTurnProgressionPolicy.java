package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic guardrail for relationship progression during live discovery.
 * AI may describe the client's reaction, but cannot turn a greeting into a
 * large relationship gain or let a short conversation satisfy a meeting gate.
 */
final class MeetingTurnProgressionPolicy {

    private static final int[] PROGRESSION_ALLOWANCE = {0, 0, 3, 8, 14, 22, 31, 40, 50, 60, 70, 80};
    private static final Set<String> CONTEXT_CUES = Set.of(
            "budget", "cost", "roi", "priority", "priorities", "risk", "risks", "timeline",
            "workflow", "workflows", "process", "processes", "integration", "implementation",
            "stakeholder", "stakeholders", "decision", "governance", "constraint", "constraints",
            "impact", "outcome", "outcomes", "mentioned", "shared", "said", "current", "existing");
    private static final Set<String> GENERIC_PROMPTS = Set.of(
            "what do you need to know", "what do you want to know", "tell me more",
            "can you explain", "please explain", "can you elaborate", "hello", "hi", "hey");
    private static final Set<String> DEFLECTING_PROMPTS = Set.of(
            "what do you need to know", "what do you want to know", "tell me more",
            "can you explain", "please explain", "can you elaborate");
    private static final Pattern UNPREPARED_RESPONSE = Pattern.compile(
            "\\b(i\\s+(do\\s+not|dont|don t|don't)\\s+know|no\\s+idea|i\\s+am\\s+not\\s+sure|not\\s+prepared)\\b");
    private static final Pattern DISMISSIVE_RESPONSE = Pattern.compile(
            "\\b(what\\s+(are|r|ur)\\s+(you\\s+)?talking\\s+about|who\\s+cares|whatever|not\\s+my\\s+problem)\\b");
    private static final Pattern EVASIVE_RESPONSE = Pattern.compile(
            "\\b(i\\s+cannot\\s+help|i\\s+can\\s*not\\s+help|can\\s*not\\s+answer|can\\s*not\\s+say|we\\s+will\\s+get\\s+back\\s+to\\s+you)\\b");

    private MeetingTurnProgressionPolicy() {}

    static PersonaStateDelta constrain(PersonaStateDelta proposed, String learnerMessage) {
        return constrain(proposed, learnerMessage, List.of());
    }

    static PersonaStateDelta constrain(PersonaStateDelta proposed, String learnerMessage,
                                       List<String> detectedLearnerBehaviours) {
        TurnQuality quality = classify(learnerMessage, detectedLearnerBehaviours);
        if (quality.isPenalty()) return quality.enforcePenalty(proposed);
        return new PersonaStateDelta(
                bound(proposed.trust(), quality.trustGainCap()),
                bound(proposed.interest(), quality.interestGainCap()),
                bound(proposed.patience(), quality.patienceGainCap()));
    }

    static int maximumScore(int initialScore, int learnerTurnNumber) {
        int index = Math.max(0, Math.min(learnerTurnNumber, PROGRESSION_ALLOWANCE.length - 1));
        return Math.min(100, initialScore + PROGRESSION_ALLOWANCE[index]);
    }

    private static int bound(int value, int positiveCap) {
        return value > 0 ? Math.min(value, positiveCap) : Math.max(value, -8);
    }

    private static TurnQuality classify(String learnerMessage, List<String> detectedLearnerBehaviours) {
        String normalized = learnerMessage == null ? "" : learnerMessage
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9? ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (DISMISSIVE_RESPONSE.matcher(normalized).find()) return TurnQuality.DISMISSIVE;
        if (UNPREPARED_RESPONSE.matcher(normalized).find()) return TurnQuality.UNPREPARED;
        if (EVASIVE_RESPONSE.matcher(normalized).find() || hasNegativeBehaviour(detectedLearnerBehaviours)) {
            return TurnQuality.EVASIVE;
        }
        if (DEFLECTING_PROMPTS.contains(normalized.replace("?", "").trim())) return TurnQuality.DEFLECTING;
        int wordCount = normalized.isBlank() ? 0 : normalized.split(" ").length;
        if (wordCount < 6 || GENERIC_PROMPTS.contains(normalized.replace("?", "").trim())) {
            return TurnQuality.LOW_SIGNAL;
        }

        long cueCount = CONTEXT_CUES.stream().filter(cue -> normalized.matches(".*\\b" + cue + "\\b.*")).count();
        boolean question = normalized.contains("?");
        if (wordCount >= 14 && cueCount >= 2) return TurnQuality.GROUNDED_DISCOVERY;
        if ((question && wordCount >= 8 && cueCount >= 1) || (wordCount >= 16 && cueCount >= 1)) {
            return TurnQuality.FOCUSED_DISCOVERY;
        }
        return TurnQuality.LOW_SIGNAL;
    }

    private static boolean hasNegativeBehaviour(List<String> behaviours) {
        return behaviours != null && behaviours.stream()
                .filter(behaviour -> behaviour != null)
                .map(behaviour -> behaviour.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'))
                .anyMatch(behaviour -> behaviour.contains("evasive")
                        || behaviour.contains("unprepared")
                        || behaviour.contains("dismissive")
                        || behaviour.contains("does_not_answer")
                        || behaviour.contains("unsupported_claim"));
    }

    private enum TurnQuality {
        LOW_SIGNAL(0, 0, 0),
        FOCUSED_DISCOVERY(2, 3, 1),
        GROUNDED_DISCOVERY(5, 5, 2),
        DEFLECTING(-6, -5, -4),
        EVASIVE(-7, -6, -5),
        UNPREPARED(-14, -12, -10),
        DISMISSIVE(-16, -14, -12);

        private final int trustGainCap;
        private final int interestGainCap;
        private final int patienceGainCap;

        TurnQuality(int trustGainCap, int interestGainCap, int patienceGainCap) {
            this.trustGainCap = trustGainCap;
            this.interestGainCap = interestGainCap;
            this.patienceGainCap = patienceGainCap;
        }

        int trustGainCap() { return trustGainCap; }
        int interestGainCap() { return interestGainCap; }
        int patienceGainCap() { return patienceGainCap; }

        boolean isPenalty() {
            return trustGainCap < 0;
        }

        PersonaStateDelta enforcePenalty(PersonaStateDelta proposed) {
            return new PersonaStateDelta(
                    Math.min(proposed.trust(), trustGainCap),
                    Math.min(proposed.interest(), interestGainCap),
                    Math.min(proposed.patience(), patienceGainCap));
        }
    }
}
