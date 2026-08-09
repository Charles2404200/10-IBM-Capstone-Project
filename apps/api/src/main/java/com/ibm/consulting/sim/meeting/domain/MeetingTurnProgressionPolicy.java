package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hybrid, auditable turn scoring for live discovery. The AI classifies observed
 * learner behaviour from the transcript; this policy verifies its shape,
 * supplies deterministic fallbacks and owns the relationship score mutation.
 */
final class MeetingTurnProgressionPolicy {

    private static final int[] PROGRESSION_ALLOWANCE = {0, 8, 16, 26, 36, 46, 56, 65, 72, 78, 84, 90};
    private static final int[] PATIENCE_PROGRESSION_ALLOWANCE = {0, 12, 24, 36, 48, 60, 70, 78, 84, 90, 95, 100};
    private static final Set<String> CONTEXT_CUES = Set.of(
            "budget", "cost", "roi", "priority", "priorities", "risk", "risks", "timeline",
            "workflow", "workflows", "process", "processes", "integration", "implementation",
            "stakeholder", "stakeholders", "decision", "governance", "constraint", "constraints",
            "impact", "outcome", "outcomes", "mentioned", "shared", "said", "current", "existing",
            "scope", "metric", "metrics", "acceptance", "owner", "owners", "approval", "approvals",
            "proposal", "pilot", "milestone", "tranche", "mobilisation", "mobilization", "accuracy",
            "data", "source", "stockout", "stockouts", "exception", "exceptions", "rollout");
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
        return constrain(proposed, learnerMessage, detectedLearnerBehaviours, null, List.of());
    }

    static PersonaStateDelta constrain(PersonaStateDelta proposed, String learnerMessage,
                                       List<String> detectedLearnerBehaviours,
                                       String clientResponse, List<String> meetingSignals) {
        TurnQuality quality = classify(learnerMessage, detectedLearnerBehaviours);
        if (quality.isPenalty()) return quality.enforcePenalty(proposed);
        PersonaStateDelta behaviourScore = scoreBehaviour(quality, detectedLearnerBehaviours);
        PersonaStateDelta assessment = applyCalibratedAiAssessment(
                behaviourScore, proposed, quality, detectedLearnerBehaviours);
        return applyClientOutcomeCredit(assessment, quality, detectedLearnerBehaviours,
                clientResponse, meetingSignals);
    }

    static int maximumScore(int initialScore, int learnerTurnNumber) {
        int index = Math.max(0, Math.min(learnerTurnNumber, PROGRESSION_ALLOWANCE.length - 1));
        return Math.min(100, initialScore + PROGRESSION_ALLOWANCE[index]);
    }

    static int maximumPatienceScore(int initialScore, int learnerTurnNumber) {
        int index = Math.max(0, Math.min(learnerTurnNumber, PATIENCE_PROGRESSION_ALLOWANCE.length - 1));
        return Math.min(100, initialScore + PATIENCE_PROGRESSION_ALLOWANCE[index]);
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

    private static PersonaStateDelta scoreBehaviour(TurnQuality quality, List<String> behaviours) {
        if (quality == TurnQuality.LOW_SIGNAL) return PersonaStateDelta.zero();

        int trust = quality.baseTrust();
        int interest = quality.baseInterest();
        int patience = quality.basePatience();
        for (String behaviour : normalizedBehaviours(behaviours)) {
            switch (behaviour) {
                case "directly_addresses_concern", "addresses_client_concern" -> {
                    trust += 2;
                    interest += 1;
                    patience += 2;
                }
                case "acknowledges_constraint" -> {
                    trust += 1;
                    patience += 2;
                }
                case "uses_client_fact", "uses_disclosed_evidence" -> {
                    trust += 2;
                    interest += 2;
                    patience += 1;
                }
                case "quantifies_business_impact", "uses_specific_metric" -> {
                    trust += 1;
                    interest += 2;
                    patience += 1;
                }
                case "asks_focused_question" -> {
                    interest += 1;
                    patience += 2;
                }
                case "grounded_recommendation" -> {
                    trust += 2;
                    interest += 1;
                    patience += 1;
                }
                default -> {
                    // Unknown labels never affect the deterministic score.
                }
            }
        }
        return new PersonaStateDelta(
                Math.min(trust, quality.trustGainCap()),
                Math.min(interest, quality.interestGainCap()),
                Math.min(patience, quality.patienceGainCap()));
    }

    private static PersonaStateDelta applyCalibratedAiAssessment(PersonaStateDelta behaviourScore,
                                                                  PersonaStateDelta proposed,
                                                                  TurnQuality quality,
                                                                  List<String> behaviours) {
        boolean verifiedPositiveBehaviour = hasPositiveBehaviour(behaviours);
        return new PersonaStateDelta(
                withinQualityCap(behaviourScore.trust()
                        + aiContribution(proposed.trust(), quality.aiTrustSupportCap(), verifiedPositiveBehaviour),
                        quality.trustGainCap()),
                withinQualityCap(behaviourScore.interest()
                        + aiContribution(proposed.interest(), quality.aiInterestSupportCap(), verifiedPositiveBehaviour),
                        quality.interestGainCap()),
                withinQualityCap(behaviourScore.patience()
                        + aiContribution(proposed.patience(), quality.aiPatienceSupportCap(), verifiedPositiveBehaviour),
                        quality.patienceGainCap()));
    }

    /**
     * A client acknowledgement is meaningful only after a focused, non-evasive
     * learner contribution. This prevents a generous model reply from rewarding a
     * greeting, while allowing an accepted plan to move at a human pace.
     */
    private static PersonaStateDelta applyClientOutcomeCredit(PersonaStateDelta assessment,
                                                               TurnQuality quality,
                                                               List<String> behaviours,
                                                               String clientResponse,
                                                               List<String> meetingSignals) {
        if (quality == TurnQuality.LOW_SIGNAL || (!hasPositiveBehaviour(behaviours)
                && quality != TurnQuality.FOCUSED_DISCOVERY && quality != TurnQuality.GROUNDED_DISCOVERY)) {
            return assessment;
        }
        ClientOutcome outcome = ClientOutcome.from(clientResponse, meetingSignals);
        return new PersonaStateDelta(
                Math.max(assessment.trust(), outcome.minimumTrustGain()),
                Math.max(assessment.interest(), outcome.minimumInterestGain()),
                Math.max(assessment.patience(), outcome.minimumPatienceGain()));
    }

    private static int aiContribution(int providerDelta, int positiveSupportCap, boolean verifiedPositiveBehaviour) {
        if (providerDelta < 0) return Math.max(providerDelta, -4);
        return verifiedPositiveBehaviour ? Math.min(providerDelta, positiveSupportCap) : 0;
    }

    private static int withinQualityCap(int value, int cap) {
        return Math.min(value, cap);
    }

    private static boolean hasNegativeBehaviour(List<String> behaviours) {
        return normalizedBehaviours(behaviours).stream()
                .anyMatch(behaviour -> behaviour.contains("evasive")
                        || behaviour.contains("unprepared")
                        || behaviour.contains("dismissive")
                        || behaviour.contains("does_not_answer")
                        || behaviour.contains("unsupported_claim"));
    }

    private static boolean hasPositiveBehaviour(List<String> behaviours) {
        return normalizedBehaviours(behaviours).stream().anyMatch(behaviour -> switch (behaviour) {
            case "directly_addresses_concern", "addresses_client_concern", "acknowledges_constraint",
                    "uses_client_fact", "uses_disclosed_evidence", "quantifies_business_impact",
                    "uses_specific_metric", "asks_focused_question", "grounded_recommendation" -> true;
            default -> false;
        });
    }

    private static Set<String> normalizedBehaviours(List<String> behaviours) {
        if (behaviours == null) return Set.of();
        return behaviours.stream()
                .filter(behaviour -> behaviour != null)
                .map(behaviour -> behaviour.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Provider signals need corroboration in the client reply. The transcript is
     * therefore the audit trail, while the model remains a bounded assessor.
     */
    private enum ClientOutcome {
        NONE(0, 0, 0),
        ACKNOWLEDGED_VALUE(6, 6, 7),
        CONFIRMED_DIRECTION(8, 8, 10),
        COMMITTED_NEXT_STEP(12, 12, 14);

        private static final Set<String> ACKNOWLEDGEMENT_PHRASES = Set.of(
                "that makes sense", "that is useful", "that s useful", "that s more like it",
                "you have my attention", "game changer", "strong commitment", "concrete plan",
                "sounds efficient", "that works");
        private static final Set<String> CONFIRMATION_PHRASES = Set.of(
                "we can agree", "comfortable with", "sounds like a solid plan", "exactly what i need",
                "completely doable", "this has real potential", "that would be fast enough",
                "definitely have my attention");
        private static final Set<String> COMMITMENT_PHRASES = Set.of(
                "ready to push it through", "let s get this moving", "lets get this moving",
                "authorizing the first tranche", "comfortable authorizing", "send over a brief proposal",
                "send that proposal", "get this pilot started", "schedule the kickoff", "start tomorrow",
                "approved internally", "clearing my schedule");

        private final int minimumTrustGain;
        private final int minimumInterestGain;
        private final int minimumPatienceGain;

        ClientOutcome(int minimumTrustGain, int minimumInterestGain, int minimumPatienceGain) {
            this.minimumTrustGain = minimumTrustGain;
            this.minimumInterestGain = minimumInterestGain;
            this.minimumPatienceGain = minimumPatienceGain;
        }

        int minimumTrustGain() { return minimumTrustGain; }
        int minimumInterestGain() { return minimumInterestGain; }
        int minimumPatienceGain() { return minimumPatienceGain; }

        static ClientOutcome from(String clientResponse, List<String> signals) {
            String response = normalize(clientResponse);
            Set<String> normalizedSignals = normalizedSignals(signals);
            if (matches(response, COMMITMENT_PHRASES)
                    || normalizedSignals.contains("client_committed_next_step")
                    && matches(response, CONFIRMATION_PHRASES)) {
                return COMMITTED_NEXT_STEP;
            }
            if (matches(response, CONFIRMATION_PHRASES)
                    || normalizedSignals.contains("client_validated_value")
                    && matches(response, ACKNOWLEDGEMENT_PHRASES)) {
                return CONFIRMED_DIRECTION;
            }
            if (matches(response, ACKNOWLEDGEMENT_PHRASES)) {
                return ACKNOWLEDGED_VALUE;
            }
            return NONE;
        }

        private static boolean matches(String value, Set<String> phrases) {
            return phrases.stream().anyMatch(value::contains);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9 ]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private static Set<String> normalizedSignals(List<String> signals) {
            if (signals == null) return Set.of();
            return signals.stream()
                    .filter(signal -> signal != null)
                    .map(signal -> signal.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private enum TurnQuality {
        LOW_SIGNAL(0, 0, 0),
        FOCUSED_DISCOVERY(3, 3, 4, 8, 8, 10, 2, 2, 2),
        GROUNDED_DISCOVERY(5, 5, 6, 12, 12, 14, 3, 3, 3),
        DEFLECTING(-6, -5, -4),
        EVASIVE(-7, -6, -5),
        UNPREPARED(-14, -12, -10),
        DISMISSIVE(-16, -14, -12);

        private final int trustGainCap;
        private final int interestGainCap;
        private final int patienceGainCap;
        private final int baseTrust;
        private final int baseInterest;
        private final int basePatience;
        private final int aiTrustSupportCap;
        private final int aiInterestSupportCap;
        private final int aiPatienceSupportCap;

        TurnQuality(int trustGainCap, int interestGainCap, int patienceGainCap) {
            this(0, 0, 0, trustGainCap, interestGainCap, patienceGainCap, 0, 0, 0);
        }

        TurnQuality(int baseTrust, int baseInterest, int basePatience,
                    int trustGainCap, int interestGainCap, int patienceGainCap) {
            this(baseTrust, baseInterest, basePatience, trustGainCap, interestGainCap, patienceGainCap, 0, 0, 0);
        }

        TurnQuality(int baseTrust, int baseInterest, int basePatience,
                    int trustGainCap, int interestGainCap, int patienceGainCap,
                    int aiTrustSupportCap, int aiInterestSupportCap, int aiPatienceSupportCap) {
            this.trustGainCap = trustGainCap;
            this.interestGainCap = interestGainCap;
            this.patienceGainCap = patienceGainCap;
            this.baseTrust = baseTrust;
            this.baseInterest = baseInterest;
            this.basePatience = basePatience;
            this.aiTrustSupportCap = aiTrustSupportCap;
            this.aiInterestSupportCap = aiInterestSupportCap;
            this.aiPatienceSupportCap = aiPatienceSupportCap;
        }

        int trustGainCap() { return trustGainCap; }
        int interestGainCap() { return interestGainCap; }
        int patienceGainCap() { return patienceGainCap; }
        int baseTrust() { return baseTrust; }
        int baseInterest() { return baseInterest; }
        int basePatience() { return basePatience; }
        int aiTrustSupportCap() { return aiTrustSupportCap; }
        int aiInterestSupportCap() { return aiInterestSupportCap; }
        int aiPatienceSupportCap() { return aiPatienceSupportCap; }

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
