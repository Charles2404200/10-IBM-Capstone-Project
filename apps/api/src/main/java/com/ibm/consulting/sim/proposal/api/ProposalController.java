package com.ibm.consulting.sim.proposal.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.proposal.application.ProposalResponse;
import com.ibm.consulting.sim.proposal.application.ProposalService;
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

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    record SubmitProposalRequest(
            @NotBlank String problemStatement,
            @NotNull List<String> components,
            @NotNull @PositiveOrZero BigDecimal budget,
            @Positive int timelineWeeks) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProposalResponse submit(@PathVariable UUID engagementId,
                             @Valid @RequestBody SubmitProposalRequest req,
                             @AuthenticationPrincipal User user) {
        return proposalService.submit(engagementId, user.getId(), req.problemStatement(),
                req.components(), req.budget(), req.timelineWeeks());
    }

    @GetMapping
    ProposalResponse get(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return proposalService.get(engagementId, user.getId());
    }
}
