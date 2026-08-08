package com.ibm.consulting.sim.lead.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.lead.application.LeadIntelligenceSummary;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.LeadSummary;
import com.ibm.consulting.sim.lead.application.ResearchEvidenceSummary;
import com.ibm.consulting.sim.lead.application.ResearchGateStatus;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    record SelectLeadRequest(@NotNull UUID leadId) {}

    record SaveResearchRequest(
            @NotBlank String note,
            String hypothesis,
            @NotNull EvidenceType evidenceType,
            @Size(max = 500) String sourceUrl,
            @Size(max = 300) String sourceTitle,
            LocalDate occurredOn,
            ConfidenceLevel confidence,
            Set<UUID> supportingEvidenceIds) {}

    @GetMapping("/scenarios/{scenarioId}/leads")
    List<LeadSummary> listLeads(@PathVariable UUID scenarioId) {
        return leadService.listForScenario(scenarioId);
    }

    @PostMapping("/engagements/{engagementId}/lead-selection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void selectLead(@PathVariable UUID engagementId,
                    @Valid @RequestBody SelectLeadRequest req,
                    @AuthenticationPrincipal User user) {
        leadService.selectLead(engagementId, req.leadId(), user.getId());
    }

    @PostMapping("/engagements/{engagementId}/research")
    @ResponseStatus(HttpStatus.CREATED)
    ResearchEvidenceSummary saveResearch(@PathVariable UUID engagementId,
                                         @Valid @RequestBody SaveResearchRequest req,
                                         @AuthenticationPrincipal User user) {
        return leadService.saveEvidence(engagementId, user.getId(),
                req.note(), req.hypothesis(), req.evidenceType(),
                req.sourceUrl(), req.sourceTitle(), req.occurredOn(),
                req.confidence() != null ? req.confidence() : ConfidenceLevel.MEDIUM,
                req.supportingEvidenceIds());
    }

    @GetMapping("/engagements/{engagementId}/research")
    List<ResearchEvidenceSummary> listResearch(@PathVariable UUID engagementId,
                                               @AuthenticationPrincipal User user) {
        return leadService.listEvidence(engagementId, user.getId());
    }

    @GetMapping("/engagements/{engagementId}/lead-intelligence")
    LeadIntelligenceSummary getLeadIntelligence(@PathVariable UUID engagementId,
                                                @AuthenticationPrincipal User user) {
        return leadService.getIntelligence(engagementId, user.getId());
    }

    @GetMapping("/engagements/{engagementId}/research-readiness")
    ResearchGateStatus getResearchReadiness(@PathVariable UUID engagementId,
                                            @AuthenticationPrincipal User user) {
        return leadService.getResearchGateStatus(engagementId, user.getId());
    }

    @PostMapping("/engagements/{engagementId}/research/complete")
    ResearchGateStatus completeResearch(@PathVariable UUID engagementId,
                                        @AuthenticationPrincipal User user) {
        return leadService.completeResearch(engagementId, user.getId());
    }
}
