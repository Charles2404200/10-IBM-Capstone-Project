package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiTrace;
import com.ibm.consulting.sim.ai.domain.AiTraceRepository;
import com.ibm.consulting.sim.ai.domain.AiTraceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Persists telemetry independently so it cannot poison a caller's business transaction. */
@Service
public class AiTraceRecorder {

    private final AiTraceRepository repository;

    public AiTraceRecorder(AiTraceRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String useCase, UUID engagementId, String provider, int promptVersion,
                       long latencyMs, AiTraceStatus status, String errorMessage) {
        repository.saveAndFlush(AiTrace.record(
                useCase, engagementId, provider, promptVersion, latencyMs, status, errorMessage));
    }
}
