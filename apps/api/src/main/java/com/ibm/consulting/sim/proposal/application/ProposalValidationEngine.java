package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import com.ibm.consulting.sim.proposal.domain.ProposalEvidenceLink;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deterministic proposal guardrail. It is deliberately independent of LLM output:
 * the learner can inspect every validation finding and the outcome remains auditable.
 */
public final class ProposalValidationEngine {
    private static final Pattern MATERIAL_NUMBER = Pattern.compile("(?:\\$|USD\\s*)?\\b\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\$\\s*\\d+(?:\\.\\d+)?");

    private ProposalValidationEngine() {}

    public static List<ProposalValidationIssue> validate(ProposalDraftContent draft, List<ProposalSource> sources) {
        return validate(draft, sources, null);
    }

    public static List<ProposalValidationIssue> validate(ProposalDraftContent draft, List<ProposalSource> sources,
                                                         DifficultyProfile profile) {
        List<ProposalValidationIssue> issues = new ArrayList<>();
        if (draft.problemStatement().length() < 20) {
            issues.add(blocking("PROBLEM_REQUIRED", "Describe the client problem with enough specificity.", "PROBLEM"));
        }
        if (meaningfulCount(draft.components()) == 0 || draft.solutionStrategy().length() < 20) {
            issues.add(blocking("SOLUTION_REQUIRED", "Explain the recommended solution and include at least one component.", "SOLUTION"));
        }
        if (draft.businessOutcomes().isEmpty()) {
            issues.add(blocking("OUTCOME_REQUIRED", "Add at least one measurable business outcome or KPI.", "OUTCOMES"));
        }
        if (draft.risks().isEmpty()) {
            issues.add(warning("RISK_COVERAGE", "No delivery or operational risks have been documented.", "RISKS"));
        }
        if (draft.milestones().isEmpty()) {
            issues.add(warning("TIMELINE_DETAIL", "Add delivery milestones so the timeline is explainable.", "TIMELINE"));
        }
        if (draft.assumptions().isEmpty()) {
            issues.add(warning("ASSUMPTIONS", "State the dependencies and assumptions behind the recommendation.", "ASSUMPTIONS"));
        }
        if (draft.evidenceLinks().isEmpty()) {
            issues.add(blocking("EVIDENCE_REQUIRED", "Attach at least one evidence or meeting source to a proposal section.", "EVIDENCE"));
        }

        Set<String> validSourceIds = sources.stream().map(ProposalSource::id).collect(Collectors.toSet());
        boolean invalidLink = draft.evidenceLinks().stream().map(ProposalEvidenceLink::getSourceId)
                .anyMatch(id -> id == null || !validSourceIds.contains(id));
        if (invalidLink) {
            issues.add(blocking("INVALID_EVIDENCE_LINK", "One or more attached sources are no longer available to this engagement.", "EVIDENCE"));
        }
        if (profile != null && !sources.isEmpty()) {
            long distinctLinks = draft.evidenceLinks().stream().map(ProposalEvidenceLink::getSourceId).distinct().count();
            int requiredSourceCount = Math.min(4, sources.size());
            int coverage = clamp(Math.round(distinctLinks * 100f / requiredSourceCount));
            if (coverage < profile.proposalEvidenceCoverageThreshold()) {
                issues.add(blocking("DIFFICULTY_EVIDENCE_COVERAGE",
                        "This scenario requires %d%% evidence coverage; the current proposal covers %d%%."
                                .formatted(profile.proposalEvidenceCoverageThreshold(), coverage), "EVIDENCE"));
            }
        }

        Set<String> groundedNumbers = extractNumbers(sources.stream().map(ProposalSource::content).toList());
        Set<String> proposalNumbers = extractNumbers(List.of(allProposalText(draft)));
        proposalNumbers.removeAll(groundedNumbers);
        // The quoted commercial estimate is allowed when the learner explicitly records it as a consultant estimate.
        if (draft.budget() != null && draft.budget().compareTo(BigDecimal.ZERO) > 0) {
            proposalNumbers.remove(normalizeNumber(draft.budget().toPlainString()));
        }
        if (!proposalNumbers.isEmpty()) {
            issues.add(warning("UNSUPPORTED_CLAIM", "Some material numerical claims are not present in linked evidence. Mark them as assumptions or attach support.", "EVIDENCE"));
        }
        return List.copyOf(issues);
    }

    public static List<ClientAlignmentItem> alignment(ProposalDraftContent draft, List<ProposalSource> sources) {
        String proposalText = allProposalText(draft);
        List<ProposalSource> clientStatements = sources.stream()
                .filter(source -> "MEETING_DISCOVERY".equals(source.type()))
                .limit(4)
                .toList();
        if (clientStatements.isEmpty()) {
            clientStatements = sources.stream().limit(4).toList();
        }
        return clientStatements.stream().map(source -> {
            int overlap = keywordOverlap(source.content(), proposalText);
            String coverage = overlap >= 4 ? "STRONG" : overlap >= 2 ? "PARTIAL" : "GAP";
            String detail = switch (coverage) {
                case "STRONG" -> "The proposal addresses this priority with grounded language.";
                case "PARTIAL" -> "The proposal references part of this priority; make the response more explicit.";
                default -> "No clear proposal response to this stated client priority.";
            };
            return new ClientAlignmentItem(source.id(), source.label(), coverage, detail);
        }).toList();
    }

    public static int problemScore(ProposalDraftContent draft) {
        return clamp((draft.problemStatement().length() >= 80 ? 55 : draft.problemStatement().length() * 55 / 80)
                + (draft.evidenceLinks().stream().anyMatch(link -> "PROBLEM".equals(link.getSection())) ? 45 : 0));
    }

    public static int evidenceScore(ProposalDraftContent draft, List<ProposalSource> sources) {
        long links = draft.evidenceLinks().stream().map(ProposalEvidenceLink::getSourceId).distinct().count();
        return sources.isEmpty() ? 0 : clamp((int) (links * 100 / Math.min(3, sources.size())));
    }

    public static int commercialScore(ProposalDraftContent draft) {
        int score = draft.budget().compareTo(BigDecimal.ZERO) > 0 ? 30 : 0;
        score += !draft.budgetSource().isBlank() ? 20 : 0;
        score += draft.businessOutcomes().stream().allMatch(outcome -> !blank(outcome.getMetric()) && !blank(outcome.getTarget()))
                && !draft.businessOutcomes().isEmpty() ? 50 : 0;
        return clamp(score);
    }

    public static int riskScore(ProposalDraftContent draft) {
        if (draft.risks().isEmpty()) return 0;
        long mitigated = draft.risks().stream().filter(risk -> !blank(risk.getMitigation())).count();
        return clamp((int) (mitigated * 100 / draft.risks().size()));
    }

    public static int feasibilityScore(ProposalDraftContent draft) {
        int score = meaningfulCount(draft.components()) > 0 ? 35 : 0;
        score += !draft.milestones().isEmpty() ? 35 : 0;
        score += !draft.assumptions().isEmpty() ? 30 : 0;
        return clamp(score);
    }

    public static int alignmentScore(List<ClientAlignmentItem> items) {
        if (items.isEmpty()) return 0;
        int total = items.stream().mapToInt(item -> switch (item.coverage()) {
            case "STRONG" -> 100;
            case "PARTIAL" -> 55;
            default -> 0;
        }).sum();
        return total / items.size();
    }

    private static ProposalValidationIssue blocking(String code, String message, String section) {
        return new ProposalValidationIssue("BLOCKING", code, message, section);
    }
    private static ProposalValidationIssue warning(String code, String message, String section) {
        return new ProposalValidationIssue("WARNING", code, message, section);
    }
    private static int meaningfulCount(Collection<String> values) {
        return (int) values.stream().filter(value -> !blank(value)).count();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int clamp(int score) { return Math.max(0, Math.min(100, score)); }
    private static String allProposalText(ProposalDraftContent draft) {
        return Stream.concat(
                        Stream.of(draft.problemStatement(), draft.solutionStrategy(), draft.budgetSource()),
                        Stream.concat(draft.components().stream(), Stream.concat(draft.assumptions().stream(),
                                Stream.concat(draft.businessOutcomes().stream().flatMap(outcome -> Stream.of(outcome.getOutcome(), outcome.getMetric(), outcome.getTarget())),
                                        Stream.concat(draft.milestones().stream().flatMap(milestone -> Stream.of(milestone.getPhase(), milestone.getDuration())),
                                                draft.risks().stream().flatMap(risk -> Stream.of(risk.getRisk(), risk.getMitigation())))))))
                .filter(value -> value != null)
                .collect(Collectors.joining(" "));
    }
    private static Set<String> extractNumbers(List<String> values) {
        Set<String> found = new HashSet<>();
        for (String value : values) {
            Matcher matcher = MATERIAL_NUMBER.matcher(value == null ? "" : value);
            while (matcher.find()) found.add(normalizeNumber(matcher.group()));
        }
        return found;
    }
    private static String normalizeNumber(String value) {
        return value.replaceAll("[^0-9.]", "").toLowerCase(Locale.ROOT);
    }
    private static int keywordOverlap(String source, String proposal) {
        Set<String> sourceWords = words(source);
        sourceWords.retainAll(words(proposal));
        return sourceWords.size();
    }
    private static Set<String> words(String value) {
        return Stream.of(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 4)
                .collect(Collectors.toSet());
    }
}
