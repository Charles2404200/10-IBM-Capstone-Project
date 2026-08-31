package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
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
            "\\b(i\\s+(do\\s+not|dont|don t|don't)\\s+(know|have\\s+enough\\s+detail)|no\\s+idea|i\\s+am\\s+not\\s+sure|not\\s+prepared)\\b");
    private static final Pattern DISMISSIVE_RESPONSE = Pattern.compile(
            "\\b(what\\s+(are|r|ur)\\s+(you\\s+)?talking\\s+about|who\\s+cares|whatever|not\\s+my\\s+problem)\\b");
    private static final Pattern EVASIVE_RESPONSE = Pattern.compile(
            "\\b(i\\s+cannot\\s+help|i\\s+can\\s*not\\s+help|can\\s*not\\s+answer|can\\s*not\\s+say|we\\s+will\\s+get\\s+back\\s+to\\s+you)\\b");
    private static final Pattern PREMATURE_RECOMMENDATION = Pattern.compile(
            "\\b(less\\s+important|move\\s+straight\\s+to|broader\\s+recommendation|ignore\\s+(the|that)\\s+concern"
                    + "|refine\\s+the\\s+remaining\\s+operational\\s+detail\\s+as\\s+the\\s+work\\s+begins"
                    + "|leave\\s+the\\s+operating\\s+constraints\\s+for\\s+the\\s+implementation\\s+plan"
                    + "|validate\\s+the\\s+client\\s+specific\\s+constraints\\s+once\\s+mobilisation\\s+begins)\\b");
    private static final Set<String> SCOREABLE_BEHAVIOURS = Set.of(
            "directly_addresses_concern", "addresses_client_concern", "acknowledges_constraint",
            "uses_client_fact", "uses_disclosed_evidence", "quantifies_business_impact",
            "uses_specific_metric", "asks_focused_question", "grounded_recommendation",
            "evasive", "unprepared", "dismissive", "does_not_answer", "unsupported_claim");
    private static final Set<String> REPETITION_STOP_WORDS = Set.of(
            "the", "and", "that", "this", "with", "from", "your", "have", "will", "would",
            "could", "should", "about", "into", "then", "than", "what", "when", "where",
            "which", "their", "there", "they", "them", "been", "being", "for", "are", "was",
            "were", "you", "our", "can", "not", "but", "how", "why", "who", "a", "an", "to", "of", "in", "on", "at", "is", "it", "we", "i");

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
        return assess(proposed, learnerMessage, detectedLearnerBehaviours, clientResponse, meetingSignals)
                .relationshipDelta();
    }

    static MeetingBehaviourAssessment assess(PersonaStateDelta proposed, String learnerMessage,
                                              List<String> detectedLearnerBehaviours,
                                              String clientResponse, List<String> meetingSignals) {
        return assess(proposed, learnerMessage, detectedLearnerBehaviours, clientResponse, meetingSignals, List.of());
    }

    static MeetingBehaviourAssessment assess(PersonaStateDelta proposed, String learnerMessage,
                                              List<String> detectedLearnerBehaviours,
                                              String clientResponse, List<String> meetingSignals,
                                              List<String> previousLearnerMessages) {
        TurnQuality quality = classify(learnerMessage, detectedLearnerBehaviours, previousLearnerMessages);
        List<String> verifiedBehaviours = normalizedBehaviours(detectedLearnerBehaviours).stream()
                .filter(SCOREABLE_BEHAVIOURS::contains)
                .sorted()
                .toList();
        if (quality.isPenalty()) {
            PersonaStateDelta penalty = quality.enforcePenalty(proposed);
            return new MeetingBehaviourAssessment(quality.name(), penalty, verifiedBehaviours,
                    penaltyExplanation(quality), recoveryAction(quality));
        }
        PersonaStateDelta behaviourScore = scoreBehaviour(quality, detectedLearnerBehaviours);
        PersonaStateDelta assessment = applyCalibratedAiAssessment(
                behaviourScore, proposed, quality, detectedLearnerBehaviours);
        PersonaStateDelta finalDelta = applyClientOutcomeCredit(assessment, quality, detectedLearnerBehaviours,
                clientResponse, meetingSignals);
        return new MeetingBehaviourAssessment(quality.name(), finalDelta, verifiedBehaviours,
                positiveExplanation(quality, finalDelta, verifiedBehaviours), nextAction(quality, finalDelta));
    }

    static int maximumScore(int initialScore, int learnerTurnNumber) {
        int index = Math.max(0, Math.min(learnerTurnNumber, PROGRESSION_ALLOWANCE.length - 1));
        return Math.min(100, initialScore + PROGRESSION_ALLOWANCE[index]);
    }

    static int maximumPatienceScore(int initialScore, int learnerTurnNumber) {
        int index = Math.max(0, Math.min(learnerTurnNumber, PATIENCE_PROGRESSION_ALLOWANCE.length - 1));
        return Math.min(100, initialScore + PATIENCE_PROGRESSION_ALLOWANCE[index]);
    }

    private static TurnQuality classify(String learnerMessage, List<String> detectedLearnerBehaviours,
                                        List<String> previousLearnerMessages) {
        String normalized = learnerMessage == null ? "" : learnerMessage
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9? ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (DISMISSIVE_RESPONSE.matcher(normalized).find()) return TurnQuality.DISMISSIVE;
        if (UNPREPARED_RESPONSE.matcher(normalized).find()) return TurnQuality.UNPREPARED;
        if (PREMATURE_RECOMMENDATION.matcher(normalized).find()) return TurnQuality.PREMATURE_RECOMMENDATION;
        if (EVASIVE_RESPONSE.matcher(normalized).find() || hasNegativeBehaviour(detectedLearnerBehaviours)) {
            return TurnQuality.EVASIVE;
        }
        if (isMeaningfullyRepeated(normalized, previousLearnerMessages)) return TurnQuality.REPETITIVE;
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

    /**
     * Prevents score farming through a paraphrased repeat while allowing a learner
     * to return to a topic with genuinely new detail. We ignore common language and
     * only flag substantial messages whose meaningful terms almost fully overlap.
     */
    private static boolean isMeaningfullyRepeated(String normalizedMessage, List<String> previousLearnerMessages) {
        Set<String> currentTerms = meaningfulTerms(normalizedMessage);
        if (currentTerms.size() < 5 || previousLearnerMessages == null) return false;

        return previousLearnerMessages.stream()
                .filter(previous -> previous != null)
                .map(previous -> meaningfulTerms(normalizeForComparison(previous)))
                .filter(previousTerms -> previousTerms.size() >= 5)
                .anyMatch(previousTerms -> similarity(currentTerms, previousTerms) >= 0.85d);
    }

    private static Set<String> meaningfulTerms(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return java.util.Arrays.stream(value.split(" "))
                .filter(term -> term.length() > 2)
                .filter(term -> !REPETITION_STOP_WORDS.contains(term))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static double similarity(Set<String> first, Set<String> second) {
        Set<String> intersection = first.stream().filter(second::contains).collect(Collectors.toSet());
        Set<String> union = new java.util.HashSet<>(first);
        union.addAll(second);
        return union.isEmpty() ? 0d : (double) intersection.size() / union.size();
    }

    private static String normalizeForComparison(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9? ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private static String positiveExplanation(TurnQuality quality, PersonaStateDelta delta, List<String> behaviours) {
        if (quality == TurnQuality.LOW_SIGNAL) {
            return "This response did not contain enough client-specific substance to change the relationship state.";
        }
        if (delta.trust() == 0 && delta.interest() == 0 && delta.patience() == 0) {
            return "The response was neutral: no verified client-specific behaviour justified a relationship change.";
        }
        String evidence = behaviours.isEmpty() ? "the focused, client-relevant response" : String.join(", ", behaviours);
        return "The Simulation Director credited " + evidence + "; the relationship change is capped by the turn-quality policy.";
    }

    private static String nextAction(TurnQuality quality, PersonaStateDelta delta) {
        if (quality == TurnQuality.LOW_SIGNAL || delta.trust() <= 0 || delta.interest() <= 0) {
            return "Address the latest client concern with one concrete fact, outcome or constraint before asking a focused question.";
        }
        return "Build on the client response: confirm the implication, then ask one focused question that advances a discovery objective.";
    }

    private static String penaltyExplanation(TurnQuality quality) {
        return switch (quality) {
            case DEFLECTING -> "The response deflected the client back to a broad question instead of advancing their stated concern.";
            case EVASIVE -> "The response avoided a client concern without offering a grounded next step.";
            case UNPREPARED -> "Saying you do not know without a constructive follow-up reduced confidence in your preparation.";
            case PREMATURE_RECOMMENDATION -> "The recommendation moved ahead of the client concern and reduced confidence in the discovery process.";
            case DISMISSIVE -> "The tone dismissed the client concern and damaged the working relationship.";
            case REPETITIVE -> "The response repeated an earlier point without adding enough new information to move discovery forward.";
            default -> "The response did not advance the client conversation.";
        };
    }

    private static String recoveryAction(TurnQuality quality) {
        return switch (quality) {
            case UNPREPARED -> "Acknowledge the gap, state how you will validate it, and ask one precise question to continue discovery.";
            case DEFLECTING, EVASIVE -> "Answer the latest concern directly using a fact already disclosed by the client.";
            case PREMATURE_RECOMMENDATION -> "Return to the client concern and validate the constraint before recommending a solution.";
            case DISMISSIVE -> "Use neutral, professional language and acknowledge the client concern before proceeding.";
            case REPETITIVE -> "Build on the client's latest answer with one new fact, constraint or decision question instead of restating the prior point.";
            default -> "Use a concrete client fact and ask one focused discovery question.";
        };
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
        PREMATURE_RECOMMENDATION(-9, -8, -7),
        DISMISSIVE(-16, -14, -12),
        REPETITIVE(-6, -5, -7);

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
