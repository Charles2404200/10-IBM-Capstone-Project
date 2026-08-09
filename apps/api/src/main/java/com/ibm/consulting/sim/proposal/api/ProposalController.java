package com.ibm.consulting.sim.proposal.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.proposal.application.*;
import com.ibm.consulting.sim.proposal.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/engagements/{engagementId}/proposal")
public class ProposalController {
    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) { this.proposalService = proposalService; }

    record OutcomeRequest(String outcome, String metric, String target) {
        ProposalBusinessOutcome toDomain() { return new ProposalBusinessOutcome(outcome, metric, target); }
    }
    record MilestoneRequest(String phase, String duration) {
        ProposalMilestone toDomain() { return new ProposalMilestone(phase, duration); }
    }
    record RiskRequest(String risk, String severity, String mitigation) {
        ProposalRisk toDomain() { return new ProposalRisk(risk, severity, mitigation); }
    }
    record EvidenceLinkRequest(String section, String sourceId) {
        ProposalEvidenceLink toDomain() { return new ProposalEvidenceLink(section, sourceId); }
    }

    record ProposalDraftRequest(
            String problemStatement,
            String solutionStrategy,
            List<String> components,
            BigDecimal budget,
            Integer timelineWeeks,
            String budgetConfidence,
            String budgetSource,
            List<OutcomeRequest> businessOutcomes,
            List<MilestoneRequest> milestones,
            List<RiskRequest> risks,
            List<String> assumptions,
            List<EvidenceLinkRequest> evidenceLinks) {
        ProposalDraftContent toContent() {
            return new ProposalDraftContent(problemStatement, solutionStrategy, components, budget,
                    timelineWeeks == null ? 1 : timelineWeeks, budgetConfidence, budgetSource,
                    businessOutcomes == null ? List.of() : businessOutcomes.stream().map(OutcomeRequest::toDomain).toList(),
                    milestones == null ? List.of() : milestones.stream().map(MilestoneRequest::toDomain).toList(),
                    risks == null ? List.of() : risks.stream().map(RiskRequest::toDomain).toList(),
                    assumptions, evidenceLinks == null ? List.of() : evidenceLinks.stream().map(EvidenceLinkRequest::toDomain).toList());
        }
    }

    /** Retained for backward-compatible callers of the original submit contract. */
    record SubmitProposalRequest(
            @NotBlank String problemStatement,
            @NotNull List<String> components,
            @NotNull @PositiveOrZero BigDecimal budget,
            @Positive int timelineWeeks,
            String solutionStrategy,
            String budgetConfidence,
            String budgetSource,
            List<OutcomeRequest> businessOutcomes,
            List<MilestoneRequest> milestones,
            List<RiskRequest> risks,
            List<String> assumptions,
            List<EvidenceLinkRequest> evidenceLinks) {
        ProposalDraftContent toContent() {
            return new ProposalDraftRequest(problemStatement, solutionStrategy, components, budget, timelineWeeks,
                    budgetConfidence, budgetSource, businessOutcomes, milestones, risks, assumptions, evidenceLinks).toContent();
        }
        boolean usesWorkspaceContract() { return solutionStrategy != null || businessOutcomes != null || evidenceLinks != null; }
    }

    @GetMapping("/workspace")
    ProposalWorkspaceResponse workspace(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return proposalService.workspace(engagementId, user.getId());
    }

    @PutMapping("/draft")
    ProposalResponse saveDraft(@PathVariable UUID engagementId, @RequestBody ProposalDraftRequest request,
                               @AuthenticationPrincipal User user) {
        return proposalService.saveDraft(engagementId, user.getId(), request.toContent());
    }

    @PostMapping("/review")
    ProposalReviewResponse review(@PathVariable UUID engagementId, @RequestBody ProposalDraftRequest request,
                                  @AuthenticationPrincipal User user) {
        return proposalService.review(engagementId, user.getId(), request.toContent());
    }

    @PostMapping("/challenge")
    ProposalChallengeResponse challenge(@PathVariable UUID engagementId, @RequestBody ProposalDraftRequest request,
                                        @AuthenticationPrincipal User user) {
        return proposalService.challenge(engagementId, user.getId(), request.toContent());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProposalResponse submit(@PathVariable UUID engagementId, @Valid @RequestBody SubmitProposalRequest request,
                            @AuthenticationPrincipal User user) {
        return proposalService.submit(engagementId, user.getId(), request.toContent(), request.usesWorkspaceContract());
    }

    @GetMapping
    ProposalResponse get(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return proposalService.get(engagementId, user.getId());
    }
}
