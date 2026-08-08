package com.ibm.consulting.sim.assessment.api;

import com.ibm.consulting.sim.assessment.application.AssessmentResponse;
import com.ibm.consulting.sim.assessment.application.AssessmentService;
import com.ibm.consulting.sim.identity.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/engagements/{engagementId}/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** Idempotent: generates the assessment on first call, returns the stored one thereafter. */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    AssessmentResponse generate(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return assessmentService.generate(engagementId, user.getId());
    }

    @GetMapping
    AssessmentResponse get(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return assessmentService.get(engagementId, user.getId());
    }
}
