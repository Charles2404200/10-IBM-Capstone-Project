package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps guided meetings instructional without making the correct answer obvious.
 * The model writes contextual choices; this policy guarantees that Easy contains
 * one credible misstep and Medium contains two, so every turn has real trade-offs.
 */
final class GuidedResponseBalancePolicy {

    private GuidedResponseBalancePolicy() {
    }

    static List<String> balance(List<String> generated, DifficultyProfile profile) {
        if (generated == null || generated.size() != 3) {
            return generated == null ? List.of() : List.copyOf(generated);
        }

        List<String> balanced = new ArrayList<>(generated);
        int requiredMissteps = profile.level() == DifficultyLevel.MEDIUM ? 2 : 1;
        int presentMissteps = (int) balanced.stream().filter(GuidedResponseBalancePolicy::isMisstep).count();
        int replacementsNeeded = Math.max(0, requiredMissteps - presentMissteps);

        for (int index = balanced.size() - 1; index >= 0 && replacementsNeeded > 0; index--) {
            if (!isMisstep(balanced.get(index))) {
                balanced.set(index, replacementsNeeded == 2 ? prematureRecommendation() : evasiveDeflection());
                replacementsNeeded--;
            }
        }
        return List.copyOf(balanced);
    }

    private static boolean isMisstep(String option) {
        String normalized = option == null ? "" : option.toLowerCase(Locale.ROOT);
        return normalized.contains("i do not have")
                || normalized.contains("i don't have")
                || normalized.contains("not enough detail")
                || normalized.contains("revisit it later")
                || normalized.contains("less important")
                || normalized.contains("move straight to")
                || normalized.contains("broader recommendation");
    }

    private static String evasiveDeflection() {
        return "I do not have enough detail to address that concern today, so perhaps we should move on and revisit it later.";
    }

    private static String prematureRecommendation() {
        return "The exact constraint is less important than the overall direction, so I suggest moving straight to a broader recommendation.";
    }
}
