package com.ibm.consulting.sim.engagement.api;

import com.ibm.consulting.sim.engagement.application.EngagementQueryService;
import com.ibm.consulting.sim.engagement.application.EngagementResponse;
import com.ibm.consulting.sim.engagement.application.StartEngagementUseCase;
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

    public EngagementController(StartEngagementUseCase startUseCase,
                                EngagementQueryService queryService) {
        this.startUseCase = startUseCase;
        this.queryService = queryService;
    }

    record StartRequest(@NotNull UUID scenarioId, UUID personaId) {}

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
}
