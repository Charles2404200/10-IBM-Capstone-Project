package com.ibm.consulting.sim.ai.application;

import java.util.List;
import java.util.Map;

/**
 * Full response payload for {@code GET /api/v1/admin/ai/operations} — provider health
 * and quota status plus the currently configured task routing table (which provider
 * would be tried first/second/third for each task type). Never exposed to learners
 * (§21 — "Sarah is typing...", not "Model: Gemini 2.5"); admin/reviewer-only.
 */
public record AiOperationsResponse(
        boolean mockMode,
        List<AiProviderStat> providers,
        Map<String, List<String>> routing) {
}
