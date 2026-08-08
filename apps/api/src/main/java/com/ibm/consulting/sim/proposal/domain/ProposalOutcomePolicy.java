package com.ibm.consulting.sim.proposal.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deterministic proposal outcome rule (§4.2, §8 Phase 3). Combines the learner's
 * relationship standing with the persona (built up during the live meeting) and
 * how well the proposed solution reflects evidence actually discovered during
 * research — never hidden scenario truth the learner never uncovered.
 *
 * Pure domain logic — no Spring/JPA dependency, fully unit-testable.
 */
public final class ProposalOutcomePolicy {

    public static final int WIN_THRESHOLD = 65;

    private static final int RELATIONSHIP_WEIGHT_PERCENT = 40;
    private static final int EVIDENCE_WEIGHT_PERCENT = 40;
    private static final int COMPREHENSIVENESS_WEIGHT_PERCENT = 20;
    private static final int MIN_COMPONENTS_FOR_FULL_CREDIT = 3;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "of", "to", "and", "in", "for", "with", "on", "our", "we", "is", "are");

    private ProposalOutcomePolicy() {}

    public static ProposalOutcome evaluate(String problemStatement, List<String> components,
                                            List<String> discoveredEvidenceNotes, int trust, int interest) {
        int relationshipScore = (trust + interest) / 2;

        Set<String> evidenceKeywords = extractKeywords(discoveredEvidenceNotes);
        Set<String> proposalKeywords = extractKeywords(
                Stream.concat(Stream.of(problemStatement == null ? "" : problemStatement), components.stream())
                        .collect(Collectors.toList()));

        int evidenceScore = evidenceKeywords.isEmpty()
                ? 0
                : (int) Math.round(100.0 * intersectionSize(evidenceKeywords, proposalKeywords) / evidenceKeywords.size());

        int comprehensivenessScore = Math.min(100,
                (int) Math.round(100.0 * meaningfulCount(components) / MIN_COMPONENTS_FOR_FULL_CREDIT));

        int weighted = (relationshipScore * RELATIONSHIP_WEIGHT_PERCENT
                + evidenceScore * EVIDENCE_WEIGHT_PERCENT
                + comprehensivenessScore * COMPREHENSIVENESS_WEIGHT_PERCENT) / 100;

        int alignmentScore = Math.max(0, Math.min(100, weighted));
        boolean won = alignmentScore >= WIN_THRESHOLD;

        String rationale = "Relationship %d/100, evidence alignment %d/100, comprehensiveness %d/100 -> overall %d/100 (%s)"
                .formatted(relationshipScore, evidenceScore, comprehensivenessScore, alignmentScore,
                        won ? "won" : "lost");

        return new ProposalOutcome(alignmentScore, won, rationale);
    }

    private static int meaningfulCount(List<String> items) {
        return (int) items.stream().filter(i -> i != null && !i.isBlank()).count();
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        List<String> shared = new ArrayList<>(a);
        shared.retainAll(b);
        return shared.size();
    }

    private static Set<String> extractKeywords(List<String> texts) {
        return texts.stream()
                .filter(t -> t != null && !t.isBlank())
                .flatMap(t -> Stream.of(t.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")))
                .filter(w -> w.length() > 3 && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }
}
