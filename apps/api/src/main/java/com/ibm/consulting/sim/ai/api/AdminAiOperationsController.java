package com.ibm.consulting.sim.ai.api;

import com.ibm.consulting.sim.ai.application.AiOperationsResponse;
import com.ibm.consulting.sim.ai.application.AiOperationsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only AI provider health/quota/routing dashboard (§21/22 of the AI
 * orchestration design doc). Restricted to reviewers/administrators — learners
 * only ever see "Sarah is typing...", never which model answered.
 */
@RestController
@RequestMapping("/api/v1/admin/ai/operations")
@PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
public class AdminAiOperationsController {

    private final AiOperationsService operationsService;

    public AdminAiOperationsController(AiOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping
    AiOperationsResponse getOperations() {
        return operationsService.snapshot();
    }
}
