package com.ibm.consulting.sim.outreach.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.outreach.application.OutreachResponse;
import com.ibm.consulting.sim.outreach.application.OutreachService;
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

    public OutreachController(OutreachService outreachService) {
        this.outreachService = outreachService;
    }

    record OutreachRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 5000) String body) {}

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
}
