package com.ibm.consulting.sim.portfolio.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.portfolio.application.PortfolioService;
import com.ibm.consulting.sim.portfolio.application.PortfolioSummaryResponse;
import com.ibm.consulting.sim.portfolio.application.ReplayComparisonResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Learner Portfolio & Progression API (Phase 4): competency trends across
 * completed engagements and side-by-side replay comparison between two
 * engagements the learner has completed.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/summary")
    PortfolioSummaryResponse summary(@AuthenticationPrincipal User user) {
        return portfolioService.getSummary(user.getId());
    }

    @GetMapping("/replay")
    ReplayComparisonResponse replay(@RequestParam UUID engagementA,
                                     @RequestParam UUID engagementB,
                                     @AuthenticationPrincipal User user) {
        return portfolioService.compare(user.getId(), engagementA, engagementB);
    }
}
