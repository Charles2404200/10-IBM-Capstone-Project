package com.ibm.consulting.sim.proposal.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Canonical decision engine for a submitted proposal. It is intentionally pure
 * and deterministic: AI may narrate the decision, but cannot alter a score,
 * outcome, condition, or evidence-support classification.
 */
public final class ProposalDecisionEngine {
    private static final int EVIDENCE_GATE = 45;

    private ProposalDecisionEngine() {}

    public static ProposalDecisionSnapshot evaluate(ProposalDraftContent proposal,
                                                    List<ProposalDecisionSource> sources,
                                                    int trust, int interest, int patience) {
        List<ProposalEvidenceImpact> evidenceImpacts = evidenceImpacts(proposal, sources);
        int clientAlignment = clientAlignment(proposal, sources);
        int evidenceGrounding = evidenceGrounding(proposal, sources, evidenceImpacts);
        int commercialLogic = commercialLogic(proposal);
        int deliveryFeasibility = deliveryFeasibility(proposal);
        int riskManagement = riskManagement(proposal);
        int stakeholderConfidence = clamp((trust + interest + patience) / 3);

        List<ProposalDecisionDimension> dimensions = List.of(
                dimension("Client alignment", clientAlignment, "How directly the recommendation responds to client priorities."),
                dimension("Evidence grounding", evidenceGrounding, "How well important proposal claims are supported by discovered sources."),
                dimension("Commercial logic", commercialLogic, "Whether investment, outcomes and assumptions are commercially explainable."),
                dimension("Delivery feasibility", deliveryFeasibility, "Whether scope, milestones and dependencies make the plan achievable."),
                dimension("Risk management", riskManagement, "Whether operational risk has mitigations and controls."),
                dimension("Stakeholder confidence", stakeholderConfidence, "Trust, interest and patience built during the engagement."));

        int decisionScore = weighted(clientAlignment, evidenceGrounding, commercialLogic, deliveryFeasibility,
                riskManagement, stakeholderConfidence);
        int learnerPerformance = Math.round((clientAlignment + evidenceGrounding + commercialLogic
                + deliveryFeasibility + riskManagement) / 5.0f);
        ClientDecisionOutcome outcome = selectOutcome(decisionScore, evidenceGrounding, commercialLogic,
                deliveryFeasibility, riskManagement, stakeholderConfidence);
        int confidence = confidence(decisionScore, evidenceGrounding, outcome);
        List<ProposalDecisionInsight> insights = insights(proposal, dimensions, evidenceImpacts, outcome);
        String rationale = "Client alignment %d, evidence grounding %d, commercial logic %d, delivery feasibility %d, risk management %d and stakeholder confidence %d produced a decision score of %d."
                .formatted(clientAlignment, evidenceGrounding, commercialLogic, deliveryFeasibility, riskManagement,
                        stakeholderConfidence, decisionScore);
        return new ProposalDecisionSnapshot(outcome, decisionScore, confidence, learnerPerformance,
                dimensions, insights, evidenceImpacts, rationale);
    }

    private static ClientDecisionOutcome selectOutcome(int score, int evidence, int commercial, int feasibility,
                                                        int risk, int stakeholder) {
        if (evidence < EVIDENCE_GATE) {
            return score >= 60 ? ClientDecisionOutcome.REVISION_REQUESTED : ClientDecisionOutcome.FURTHER_DISCOVERY_REQUIRED;
        }
        if (score >= 90 && stakeholder >= 85 && commercial >= 80 && risk >= 80) return ClientDecisionOutcome.STRATEGIC_PARTNERSHIP;
        if (score >= 78 && feasibility >= 70 && risk >= 70) return ClientDecisionOutcome.PILOT_APPROVED;
        if (score >= 70) return ClientDecisionOutcome.PROPOSAL_ACCEPTED;
        if (score >= 58) return ClientDecisionOutcome.REVISION_REQUESTED;
        if (score >= 45) return ClientDecisionOutcome.FURTHER_DISCOVERY_REQUIRED;
        if (score >= 35) return ClientDecisionOutcome.DEFERRED;
        return ClientDecisionOutcome.REJECTED;
    }

    private static int weighted(int alignment, int evidence, int commercial, int feasibility, int risk, int stakeholder) {
        return clamp(Math.round(alignment * .25f + evidence * .20f + commercial * .15f
                + feasibility * .15f + risk * .15f + stakeholder * .10f));
    }

    private static int clientAlignment(ProposalDraftContent proposal, List<ProposalDecisionSource> sources) {
        List<ProposalDecisionSource> clientSources = sources.stream()
                .filter(source -> "MEETING_DISCOVERY".equals(source.type()))
                .toList();
        List<ProposalDecisionSource> priorities = clientSources.isEmpty() ? sources : clientSources;
        if (priorities.isEmpty()) return 0;
        String proposalText = proposalText(proposal);
        return clamp((int) Math.round(priorities.stream()
                .mapToInt(source -> Math.min(100, keywordOverlap(source.content(), proposalText) * 20))
                .average().orElse(0)));
    }

    private static int evidenceGrounding(ProposalDraftContent proposal, List<ProposalDecisionSource> sources,
                                         List<ProposalEvidenceImpact> impacts) {
        if (sources.isEmpty()) return 0;
        Set<String> linked = proposal.evidenceLinks().stream().map(ProposalEvidenceLink::getSourceId).collect(Collectors.toSet());
        int linkCoverage = clamp(Math.round(linked.size() * 100f / Math.min(4, sources.size())));
        int supportedClaims = (int) impacts.stream().filter(impact -> "WELL_SUPPORTED".equals(impact.getSupportLevel())).count();
        int partialClaims = (int) impacts.stream().filter(impact -> "PARTIALLY_SUPPORTED".equals(impact.getSupportLevel())).count();
        int claimScore = impacts.isEmpty() ? 0 : clamp(Math.round((supportedClaims * 100f + partialClaims * 55f) / impacts.size()));
        return clamp(Math.round(linkCoverage * .45f + claimScore * .55f));
    }

    private static int commercialLogic(ProposalDraftContent proposal) {
        int score = proposal.budget().compareTo(BigDecimal.ZERO) > 0 ? 25 : 0;
        score += nonBlank(proposal.budgetSource()) ? 20 : 0;
        score += !"UNCONFIRMED".equalsIgnoreCase(proposal.budgetConfidence()) ? 10 : 0;
        score += proposal.businessOutcomes().isEmpty() ? 0 : 20;
        score += proposal.businessOutcomes().stream().allMatch(outcome -> nonBlank(outcome.getMetric()) && nonBlank(outcome.getTarget()))
                && !proposal.businessOutcomes().isEmpty() ? 25 : 0;
        return clamp(score);
    }

    private static int deliveryFeasibility(ProposalDraftContent proposal) {
        int score = meaningful(proposal.components()) > 0 ? 30 : 0;
        score += !proposal.milestones().isEmpty() ? 35 : 0;
        score += meaningful(proposal.assumptions()) > 0 ? 20 : 0;
        score += proposal.timelineWeeks() > 0 && proposal.timelineWeeks() <= 16 ? 15 : 0;
        return clamp(score);
    }

    private static int riskManagement(ProposalDraftContent proposal) {
        if (proposal.risks().isEmpty()) return 0;
        int mitigated = (int) proposal.risks().stream().filter(risk -> nonBlank(risk.getMitigation())).count();
        int highRiskControlled = (int) proposal.risks().stream()
                .filter(risk -> "HIGH".equalsIgnoreCase(risk.getSeverity()))
                .filter(risk -> nonBlank(risk.getMitigation())).count();
        int base = Math.round(mitigated * 75f / proposal.risks().size());
        return clamp(base + (highRiskControlled > 0 ? 25 : 0));
    }

    private static List<ProposalEvidenceImpact> evidenceImpacts(ProposalDraftContent proposal,
                                                                  List<ProposalDecisionSource> sources) {
        List<String> claims = new ArrayList<>();
        addIfMeaningful(claims, proposal.problemStatement());
        addIfMeaningful(claims, proposal.solutionStrategy());
        proposal.components().forEach(component -> addIfMeaningful(claims, component));
        proposal.businessOutcomes().forEach(outcome -> addIfMeaningful(claims, outcome.getOutcome()));
        if (claims.isEmpty()) return List.of();
        Set<String> linkedIds = proposal.evidenceLinks().stream().map(ProposalEvidenceLink::getSourceId).collect(Collectors.toSet());
        List<ProposalDecisionSource> linkedSources = sources.stream().filter(source -> linkedIds.contains(source.id())).toList();
        return claims.stream().limit(12).map(claim -> {
            int overlap = linkedSources.stream().mapToInt(source -> keywordOverlap(claim, source.content())).max().orElse(0);
            String level = overlap >= 3 ? "WELL_SUPPORTED" : overlap >= 1 ? "PARTIALLY_SUPPORTED" : "UNSUPPORTED";
            String explanation = switch (level) {
                case "WELL_SUPPORTED" -> "A linked research or meeting source directly supports this claim.";
                case "PARTIALLY_SUPPORTED" -> "A linked source is related, but the claim needs more specific validation.";
                default -> "No linked source directly supports this claim; mark it as an assumption or validate it with the client.";
            };
            return new ProposalEvidenceImpact(shortClaim(claim), level, explanation);
        }).toList();
    }

    private static List<ProposalDecisionInsight> insights(ProposalDraftContent proposal,
                                                            List<ProposalDecisionDimension> dimensions,
                                                            List<ProposalEvidenceImpact> impacts,
                                                            ClientDecisionOutcome outcome) {
        List<ProposalDecisionInsight> insights = new ArrayList<>();
        dimensions.stream().filter(dimension -> dimension.getScore() >= 75).limit(3)
                .forEach(dimension -> insights.add(new ProposalDecisionInsight("STRENGTH",
                        dimension.getDimension() + " was strong at " + dimension.getScore() + "/100.")));
        dimensions.stream().filter(dimension -> dimension.getScore() < 65).limit(3)
                .forEach(dimension -> insights.add(new ProposalDecisionInsight("CONCERN",
                        dimension.getDimension() + " needs improvement at " + dimension.getScore() + "/100.")));
        if (impacts.stream().anyMatch(impact -> "UNSUPPORTED".equals(impact.getSupportLevel()))) {
            insights.add(new ProposalDecisionInsight("CONCERN", "Some important proposal claims were not directly supported by attached evidence."));
        }
        if (outcome == ClientDecisionOutcome.PILOT_APPROVED) {
            insights.add(new ProposalDecisionInsight("CONDITION", "Pilot scope is limited to the validated workflow and must meet agreed success criteria before expansion."));
        }
        if (proposal.budgetConfidence().equalsIgnoreCase("UNCONFIRMED")) {
            insights.add(new ProposalDecisionInsight("CONDITION", "Final commercial approval is subject to budget validation with the client."));
        }
        if (proposal.risks().stream().anyMatch(risk -> "HIGH".equalsIgnoreCase(risk.getSeverity()))) {
            insights.add(new ProposalDecisionInsight("CONDITION", "High-risk controls and rollback thresholds must be confirmed before delivery begins."));
        }
        return List.copyOf(insights);
    }

    private static ProposalDecisionDimension dimension(String name, int score, String description) {
        String interpretation = score >= 80 ? "Strong" : score >= 65 ? "Good" : score >= 45 ? "Needs improvement" : "Critical gap";
        return new ProposalDecisionDimension(name, score, interpretation + ": " + description);
    }
    private static int confidence(int decisionScore, int evidence, ClientDecisionOutcome outcome) {
        if (outcome == ClientDecisionOutcome.REJECTED || outcome == ClientDecisionOutcome.DEFERRED) return decisionScore >= 45 ? 70 : 85;
        return clamp(Math.round(decisionScore * .65f + evidence * .35f));
    }
    private static String proposalText(ProposalDraftContent proposal) {
        return Stream.concat(Stream.of(proposal.problemStatement(), proposal.solutionStrategy()), Stream.concat(
                proposal.components().stream(), proposal.businessOutcomes().stream().flatMap(outcome -> Stream.of(outcome.getOutcome(), outcome.getMetric(), outcome.getTarget()))))
                .filter(value -> value != null).collect(Collectors.joining(" "));
    }
    private static int keywordOverlap(String left, String right) {
        Set<String> words = words(left);
        words.retainAll(words(right));
        return words.size();
    }
    private static Set<String> words(String text) {
        return Arrays.stream((text == null ? "" : text.toLowerCase(Locale.ROOT)).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 4).collect(Collectors.toCollection(HashSet::new));
    }
    private static int meaningful(Collection<String> values) { return (int) values.stream().filter(ProposalDecisionEngine::nonBlank).count(); }
    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private static void addIfMeaningful(List<String> target, String value) { if (nonBlank(value)) target.add(value); }
    private static String shortClaim(String value) { return value.length() <= 180 ? value : value.substring(0, 177) + "..."; }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
