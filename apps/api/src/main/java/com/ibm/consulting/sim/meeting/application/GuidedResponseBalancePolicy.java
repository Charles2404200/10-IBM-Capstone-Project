package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Keeps guided meetings instructional without making the correct answer obvious.
 * The model writes contextual choices; this policy guarantees that Easy contains
 * one professional near-miss and Medium contains two. Near-misses are consulting
 * mistakes, not visibly bad language: they either commit before validating, shift
 * away from the client's stated concern, or defer validation too late.
 */
final class GuidedResponseBalancePolicy {

    private GuidedResponseBalancePolicy() {
    }

    static List<String> balance(List<String> generated, DifficultyProfile profile) {
        return balance(generated, profile, 0);
    }

    /**
     * Uses the source turn as a deterministic shuffle key. This prevents learners
     * from inferring quality from a fixed option position while keeping replays
     * and audits reproducible.
     */
    static List<String> balance(List<String> generated, DifficultyProfile profile, int sourceSequence) {
        if (generated == null || generated.size() != 3) {
            return generated == null ? List.of() : List.copyOf(generated);
        }

        List<String> balanced = new ArrayList<>(generated);
        int requiredMissteps = profile.level() == DifficultyLevel.MEDIUM ? 2 : 1;
        List<Integer> nearMissIndexes = indexesOfNearMisses(balanced);
        while (nearMissIndexes.size() > requiredMissteps) {
            int index = nearMissIndexes.remove(nearMissIndexes.size() - 1);
            balanced.set(index, validationFirstResponse());
        }

        List<Integer> strongIndexes = new ArrayList<>();
        for (int index = 0; index < balanced.size(); index++) {
            if (!isNearMiss(balanced.get(index))) {
                strongIndexes.add(index);
            }
        }

        int replacementsNeeded = Math.max(0, requiredMissteps - indexesOfNearMisses(balanced).size());
        for (int replacement = 0; replacement < replacementsNeeded && !strongIndexes.isEmpty(); replacement++) {
            int strongIndex = strongIndexes.remove(strongIndexes.size() - 1);
            balanced.set(strongIndex, nearMiss(sourceSequence + replacement));
        }

        Collections.rotate(balanced, Math.floorMod(sourceSequence, balanced.size()));
        return List.copyOf(balanced);
    }

    private static List<Integer> indexesOfNearMisses(List<String> options) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            if (isNearMiss(options.get(index))) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    static boolean isNearMiss(String option) {
        String normalized = option == null ? "" : option.toLowerCase(Locale.ROOT);
        return normalized.contains("i do not have")
                || normalized.contains("i don't have")
                || normalized.contains("not enough detail")
                || normalized.contains("revisit it later")
                || normalized.contains("less important")
                || normalized.contains("move straight to")
                || normalized.contains("broader recommendation")
                || normalized.contains("refine the remaining operational detail as the work begins")
                || normalized.contains("leave the operating constraints for the implementation plan")
                || normalized.contains("validate the client-specific constraints once mobilisation begins");
    }

    private static String nearMiss(int variant) {
        return switch (Math.floorMod(variant, 3)) {
            case 0 -> "Based on the direction so far, I would frame the next phase around a focused pilot and refine the remaining operational detail as the work begins.";
            case 1 -> "To keep momentum, I would prioritise the technical workstream first and leave the operating constraints for the implementation plan.";
            default -> "We can use the standard pilot pattern from comparable programmes, then validate the client-specific constraints once mobilisation begins.";
        };
    }

    private static String validationFirstResponse() {
        return "Before we commit scope, could we confirm the client constraint, accountable owner and measurable outcome that should govern the next step?";
    }
}
