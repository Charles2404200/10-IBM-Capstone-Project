package com.ibm.consulting.sim.assessment.api;

import com.ibm.consulting.sim.assessment.application.AssessmentResponse;
import com.ibm.consulting.sim.assessment.application.AssessmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Coaching/review view of any learner's assessment, independent of engagement
 * ownership. Restricted to {@code REVIEWER} and {@code ADMINISTRATOR} — supports
 * the "hybrid deterministic + AI-assisted evaluation with evidence-based feedback"
 * capability by letting a human coach audit the same evidence the learner sees.
 */
@RestController
@RequestMapping("/api/v1/admin/engagements/{engagementId}/assessment")
@PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
public class AdminAssessmentController {

    private final AssessmentService assessmentService;

    public AdminAssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping
    AssessmentResponse getForReview(@PathVariable UUID engagementId) {
        return assessmentService.getForReview(engagementId);
    }
}
