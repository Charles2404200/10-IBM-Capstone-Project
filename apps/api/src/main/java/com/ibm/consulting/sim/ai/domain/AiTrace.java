package com.ibm.consulting.sim.ai.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * Immutable audit record of a single AI invocation.
 * Captures enough detail (model, prompt version, latency, status) to satisfy
 * §7 observability requirements and §5.4 trace-storage rules without exposing
 * raw prompts to the learner-facing API.
 */
@Entity
@Table(name = "ai_traces")
public class AiTrace extends BaseEntity {

    @Column(nullable = false)
    private String useCase;

    private UUID engagementId;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int promptVersion;

    @Column(nullable = false)
    private long latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiTraceStatus status;

    @Column(columnDefinition = "text")
    private String errorMessage;

    protected AiTrace() {}

    public static AiTrace record(String useCase, UUID engagementId, String model, int promptVersion,
                                  long latencyMs, AiTraceStatus status, String errorMessage) {
        AiTrace t = new AiTrace();
        t.useCase = useCase;
        t.engagementId = engagementId;
        t.model = model;
        t.promptVersion = promptVersion;
        t.latencyMs = latencyMs;
        t.status = status;
        t.errorMessage = errorMessage;
        return t;
    }

    public String getUseCase() { return useCase; }
    public UUID getEngagementId() { return engagementId; }
    public String getModel() { return model; }
    public int getPromptVersion() { return promptVersion; }
    public long getLatencyMs() { return latencyMs; }
    public AiTraceStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
