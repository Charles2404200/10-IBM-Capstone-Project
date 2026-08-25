package com.ibm.consulting.sim.shared.domain.kafka;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class UnsupportedKafkaEventException
        extends DomainException {

    public UnsupportedKafkaEventException(
            String message
    ) {
        super(message);
    }
}