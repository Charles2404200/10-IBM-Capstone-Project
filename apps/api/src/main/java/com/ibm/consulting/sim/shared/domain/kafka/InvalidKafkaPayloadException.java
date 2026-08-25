package com.ibm.consulting.sim.shared.domain.kafka;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidKafkaPayloadException
        extends DomainException {

    public InvalidKafkaPayloadException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}