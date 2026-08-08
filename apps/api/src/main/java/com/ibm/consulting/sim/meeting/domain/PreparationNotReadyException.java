package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class PreparationNotReadyException extends DomainException {
    public PreparationNotReadyException(int readinessScore) {
        super("Meeting preparation is not ready (readiness=%d, required=%d)"
                .formatted(readinessScore, ReadinessPolicy.READY_THRESHOLD));
    }
}
