package com.ibm.consulting.sim.ai.domain;

/** Domain-neutral contract for the AI model gateway. No Spring/HTTP types here. */
public interface AiModelGateway {

    /**
     * Sends a prompt and returns a structured JSON string response.
     *
     * @param useCase  logical use-case label for tracing (e.g. "outreach_evaluation")
     * @param prompt   the assembled system + user prompt
     * @return validated JSON string matching the expected schema for the use-case
     */
    String complete(String useCase, String prompt);
}
