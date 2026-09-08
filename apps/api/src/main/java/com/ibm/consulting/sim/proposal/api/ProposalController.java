package com.ibm.consulting.sim.proposal.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.proposal.application.*;
import com.ibm.consulting.sim.proposal.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/engagements/{engagementId}/proposal")
public class ProposalController {
    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) { this.proposalService = proposalService; }

    record OutcomeRequest(
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String outcome,
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String metric,
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String target) {
        ProposalBusinessOutcome toDomain() { return new ProposalBusinessOutcome(outcome, metric, target); }
    }
    record MilestoneRequest(
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String phase,
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String duration) {
        ProposalMilestone toDomain() { return new ProposalMilestone(phase, duration); }
    }
    record RiskRequest(
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String risk,
            @Pattern(regexp = "(?:|LOW|MEDIUM|HIGH)") String severity,
            @Size(max = ProposalRequestLimits.NARRATIVE_MAX_LENGTH) String mitigation) {
        ProposalRisk toDomain() { return new ProposalRisk(risk, severity, mitigation); }
    }
    record EvidenceLinkRequest(
            @NotBlank @Size(max = ProposalRequestLimits.EVIDENCE_SECTION_MAX_LENGTH) String section,
            @NotBlank @Size(max = ProposalRequestLimits.EVIDENCE_SOURCE_ID_MAX_LENGTH) String sourceId) {
        ProposalEvidenceLink toDomain() { return new ProposalEvidenceLink(section, sourceId); }
    }

    record ProposalDraftRequest(
            @Size(max = ProposalRequestLimits.NARRATIVE_MAX_LENGTH) String problemStatement,
            @Size(max = ProposalRequestLimits.NARRATIVE_MAX_LENGTH) String solutionStrategy,
            @Size(max = ProposalRequestLimits.MAX_ITEMS)
            List<@NotNull @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String> components,
            @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal budget,
            @Positive Integer timelineWeeks,
            @Pattern(regexp = "(?:|UNCONFIRMED|LOW|MEDIUM|HIGH)") String budgetConfidence,
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String budgetSource,
            @Size(max = ProposalRequestLimits.MAX_ITEMS) List<@NotNull @Valid OutcomeRequest> businessOutcomes,
            @Size(max = ProposalRequestLimits.MAX_ITEMS) List<@NotNull @Valid MilestoneRequest> milestones,
            @Size(max = ProposalRequestLimits.MAX_ITEMS) List<@NotNull @Valid RiskRequest> risks,
            @Size(max = ProposalRequestLimits.MAX_ITEMS)
            List<@NotNull @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String> assumptions,
            @Size(max = ProposalRequestLimits.MAX_EVIDENCE_LINKS)
            List<@NotNull @Valid EvidenceLinkRequest> evidenceLinks) {
        ProposalDraftContent toContent() {
            return new ProposalDraftContent(problemStatement, solutionStrategy, components, budget,
                    timelineWeeks == null ? 1 : timelineWeeks, budgetConfidence, budgetSource,
                    nonNull(businessOutcomes).stream().map(OutcomeRequest::toDomain).toList(),
                    nonNull(milestones).stream().map(MilestoneRequest::toDomain).toList(),
                    nonNull(risks).stream().map(RiskRequest::toDomain).toList(),
                    nonNull(assumptions), nonNull(evidenceLinks).stream().map(EvidenceLinkRequest::toDomain).toList());
        }

        private static <T> List<T> nonNull(List<T> values) {
            return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
        }
    }

    /** Retained for backward-compatible callers of the original submit contract. */
    record SubmitProposalRequest(
            @NotBlank @Size(max = ProposalRequestLimits.NARRATIVE_MAX_LENGTH) String problemStatement,
            @NotNull @Size(max = ProposalRequestLimits.MAX_ITEMS)
            List<@NotNull @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String> components,
            @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal budget,
            @Positive int timelineWeeks,
            @Size(max = ProposalRequestLimits.NARRATIVE_MAX_LENGTH) String solutionStrategy,
            @Pattern(regexp = "(?:|UNCONFIRMED|LOW|MEDIUM|HIGH)") String budgetConfidence,
            @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String budgetSource,
            @Size(max = ProposalRequestLimits.MAX_ITEMS) List<@NotNull @Valid OutcomeRequest> businessOutcomes,
            @Size(max = ProposalRequestLimits.MAX_ITEMS) List<@NotNull @Valid MilestoneRequest> milestones,
            @Size(max = ProposalRequestLimits.MAX_ITEMS) List<@NotNull @Valid RiskRequest> risks,
            @Size(max = ProposalRequestLimits.MAX_ITEMS)
            List<@NotNull @Size(max = ProposalRequestLimits.ITEM_MAX_LENGTH) String> assumptions,
            @Size(max = ProposalRequestLimits.MAX_EVIDENCE_LINKS)
            List<@NotNull @Valid EvidenceLinkRequest> evidenceLinks) {
        ProposalDraftContent toContent() {
            return new ProposalDraftRequest(problemStatement, solutionStrategy, components, budget, timelineWeeks,
                    budgetConfidence, budgetSource, businessOutcomes, milestones, risks, assumptions, evidenceLinks).toContent();
        }
        boolean usesWorkspaceContract() {
            return solutionStrategy != null
                    || budgetConfidence != null
                    || budgetSource != null
                    || businessOutcomes != null
                    || milestones != null
                    || risks != null
                    || assumptions != null
                    || evidenceLinks != null;
        }
    }

    @GetMapping("/workspace")
    ProposalWorkspaceResponse workspace(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return proposalService.workspace(engagementId, user.getId());
    }

    @PutMapping("/draft")
    ProposalResponse saveDraft(@PathVariable UUID engagementId, @Valid @RequestBody ProposalDraftRequest request,
                               @AuthenticationPrincipal User user) {
        return proposalService.saveDraft(engagementId, user.getId(), request.toContent());
    }

    @PostMapping("/review")
    ProposalReviewResponse review(@PathVariable UUID engagementId, @Valid @RequestBody ProposalDraftRequest request,
                                  @AuthenticationPrincipal User user) {
        return proposalService.review(engagementId, user.getId(), request.toContent());
    }

    @PostMapping("/challenge")
    ProposalChallengeResponse challenge(@PathVariable UUID engagementId, @Valid @RequestBody ProposalDraftRequest request,
                                        @AuthenticationPrincipal User user) {
        return proposalService.challenge(engagementId, user.getId(), request.toContent());
    }

    @PostMapping("/decision/explanation")
    ProposalDecisionExplanationResponse explainDecision(@PathVariable UUID engagementId,
                                                         @AuthenticationPrincipal User user) {
        return proposalService.explainDecision(engagementId, user.getId());
    }

    @PostMapping("/decision/counterfactual")
    ProposalDecisionExplanationResponse counterfactual(@PathVariable UUID engagementId,
                                                        @AuthenticationPrincipal User user) {
        return proposalService.counterfactual(engagementId, user.getId());
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
