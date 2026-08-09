package com.ibm.consulting.sim.outreach.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.outreach.application.OutreachResponse;
import com.ibm.consulting.sim.outreach.application.OutreachService;
import com.ibm.consulting.sim.outreach.application.CapabilityBriefResponse;
import com.ibm.consulting.sim.outreach.application.CapabilityBriefService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/engagements/{engagementId}/outreach")
public class OutreachController {

    private final OutreachService outreachService;
    private final CapabilityBriefService capabilityBriefService;

    public OutreachController(OutreachService outreachService, CapabilityBriefService capabilityBriefService) {
        this.outreachService = outreachService;
        this.capabilityBriefService = capabilityBriefService;
    }

    record OutreachRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 5000) String body) {}

    record CapabilityBriefRequest(
            @NotBlank @Size(max = 3000) String relevantExperience,
            @NotBlank @Size(max = 3000) String approach,
            @NotBlank @Size(max = 3000) String caseExample,
            @NotBlank @Size(max = 3000) String clientFit) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OutreachResponse send(@PathVariable UUID engagementId,
                          @Valid @RequestBody OutreachRequest req,
                          @AuthenticationPrincipal User user) {
        return outreachService.send(engagementId, user.getId(), req.subject(), req.body());
    }

    @GetMapping
    List<OutreachResponse> list(@PathVariable UUID engagementId,
                                @AuthenticationPrincipal User user) {
        return outreachService.listAttempts(engagementId, user.getId());
    }

    @GetMapping("/capability-brief")
    CapabilityBriefResponse capabilityBrief(@PathVariable UUID engagementId,
                                            @AuthenticationPrincipal User user) {
        return capabilityBriefService.get(engagementId, user.getId());
    }

    @PostMapping("/capability-brief")
    @ResponseStatus(HttpStatus.CREATED)
    CapabilityBriefResponse submitCapabilityBrief(@PathVariable UUID engagementId,
                                                  @Valid @RequestBody CapabilityBriefRequest req,
                                                  @AuthenticationPrincipal User user) {
        return capabilityBriefService.submit(engagementId, user.getId(), req.relevantExperience(),
                req.approach(), req.caseExample(), req.clientFit());
    }
}
