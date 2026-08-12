package com.ibm.consulting.sim.engagement.api;

import com.ibm.consulting.sim.engagement.application.EngagementQueryService;
import com.ibm.consulting.sim.engagement.application.EngagementResponse;
import com.ibm.consulting.sim.engagement.application.StartEngagementUseCase;
import com.ibm.consulting.sim.engagement.application.RetryEngagementUseCase;
import com.ibm.consulting.sim.identity.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/engagements")
public class EngagementController {

    private final StartEngagementUseCase startUseCase;
    private final EngagementQueryService queryService;
    private final RetryEngagementUseCase retryUseCase;

    public EngagementController(StartEngagementUseCase startUseCase,
                                EngagementQueryService queryService,
                                RetryEngagementUseCase retryUseCase) {
        this.startUseCase = startUseCase;
        this.queryService = queryService;
        this.retryUseCase = retryUseCase;
    }

    record StartRequest(@NotNull UUID scenarioId, UUID personaId) {}
    record StartFromLeadRequest(@NotNull UUID leadId, UUID personaId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EngagementResponse start(@Valid @RequestBody StartRequest req,
                             @AuthenticationPrincipal User user) {
        return startUseCase.execute(user.getId(), req.scenarioId(), req.personaId());
    }

    @GetMapping
    List<EngagementResponse> listMine(@AuthenticationPrincipal User user) {
        return queryService.listForUser(user.getId());
    }

    @GetMapping("/{id}")
    EngagementResponse getWorkspace(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return queryService.getWorkspace(id, user.getId());
    }

    /** Catalogue shortcut; legacy scenario-first start stays available above. */
    @PostMapping("/from-lead")
    @ResponseStatus(HttpStatus.CREATED)
    EngagementResponse startFromLead(@Valid @RequestBody StartFromLeadRequest req,
                                     @AuthenticationPrincipal User user) {
        return startUseCase.executeForLead(user.getId(), req.leadId(), req.personaId());
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    EngagementResponse retryFromLead(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return retryUseCase.execute(id, user.getId());
    }
}
