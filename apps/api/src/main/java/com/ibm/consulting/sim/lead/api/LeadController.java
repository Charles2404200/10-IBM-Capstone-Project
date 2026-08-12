package com.ibm.consulting.sim.lead.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.lead.application.LeadIntelligenceSummary;
import com.ibm.consulting.sim.lead.application.LeadCatalogResponse;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.LeadSummary;
import com.ibm.consulting.sim.lead.domain.LeadCatalogQuery;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.application.ResearchArtifactResponse;
import com.ibm.consulting.sim.lead.application.ResearchEvidenceSummary;
import com.ibm.consulting.sim.lead.application.ResearchGateStatus;
import com.ibm.consulting.sim.lead.application.ResearchIntelligenceService;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceVerificationStatus;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private final ResearchIntelligenceService researchIntelligenceService;

    public LeadController(LeadService leadService,
                          ResearchIntelligenceService researchIntelligenceService) {
        this.leadService = leadService;
        this.researchIntelligenceService = researchIntelligenceService;
    }

    record SelectLeadRequest(@NotNull UUID leadId) {}

    record SaveResearchRequest(
            @NotBlank String note,
            String hypothesis,
            @NotNull EvidenceType evidenceType,
            @Size(max = 500) String sourceUrl,
            @Size(max = 300) String sourceTitle,
            EvidenceOrigin origin,
            EvidenceVerificationStatus verificationStatus,
            LocalDate occurredOn,
            ConfidenceLevel confidence,
            @Min(0) @Max(100) Integer relevanceScore,
            Set<UUID> supportingEvidenceIds) {}

    record GenerateResearchRequest(@NotNull EvidenceType evidenceType) {}

    record AnalyzeUserContextRequest(@NotBlank @Size(max = 4000) String context) {}

    @GetMapping("/scenarios/{scenarioId}/leads")
    List<LeadSummary> listLeads(@PathVariable UUID scenarioId) {
        return leadService.listForScenario(scenarioId);
    }

    /** Additive catalogue endpoint. The legacy per-scenario list remains for the existing lead pipeline. */
    @GetMapping("/lead-catalog")
    LeadCatalogResponse listCatalog(@RequestParam(name = "scenarioId", required = false) UUID scenarioId,
                                    @RequestParam(name = "search", required = false) String search,
                                    @RequestParam(name = "industry", required = false) String industry,
                                    @RequestParam(name = "difficulty", required = false) LeadDifficulty difficulty,
                                    @RequestParam(name = "page", defaultValue = "0") int page,
                                    @RequestParam(name = "size", defaultValue = "12") int size) {
        return leadService.listCatalog(new LeadCatalogQuery(scenarioId, search, industry, difficulty, page, size));
    }

    @GetMapping("/lead-catalog/industries")
    List<String> listCatalogIndustries() {
        return leadService.catalogIndustries();
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
                req.sourceUrl(), req.sourceTitle(),
                req.origin() != null ? req.origin() : EvidenceOrigin.USER_SUPPLIED,
                req.verificationStatus() != null ? req.verificationStatus()
                        : defaultVerification(req.origin()),
                req.occurredOn(),
                req.confidence() != null ? req.confidence() : ConfidenceLevel.MEDIUM,
                req.relevanceScore(),
                req.supportingEvidenceIds());
    }

    private EvidenceVerificationStatus defaultVerification(EvidenceOrigin origin) {
        return origin == null || origin == EvidenceOrigin.USER_SUPPLIED
                ? EvidenceVerificationStatus.UNVERIFIED
                : EvidenceVerificationStatus.CORROBORATED;
    }

    @GetMapping("/engagements/{engagementId}/research")
    List<ResearchEvidenceSummary> listResearch(@PathVariable UUID engagementId,
                                               @AuthenticationPrincipal User user) {
        return leadService.listEvidence(engagementId, user.getId());
    }

    @PostMapping("/engagements/{engagementId}/research-intelligence")
    List<ResearchArtifactResponse> generateResearch(@PathVariable UUID engagementId,
                                                    @Valid @RequestBody GenerateResearchRequest req,
                                                    @AuthenticationPrincipal User user) {
        return researchIntelligenceService.generate(engagementId, user.getId(), req.evidenceType());
    }

    @PostMapping("/engagements/{engagementId}/research-intelligence/user-context")
    ResearchArtifactResponse analyzeUserContext(@PathVariable UUID engagementId,
                                                @Valid @RequestBody AnalyzeUserContextRequest req,
                                                @AuthenticationPrincipal User user) {
        return researchIntelligenceService.analyzeUserContext(engagementId, user.getId(), req.context());
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
