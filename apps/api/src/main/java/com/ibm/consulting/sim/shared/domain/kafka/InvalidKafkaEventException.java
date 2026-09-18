package com.ibm.consulting.sim.shared.domain.kafka;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidKafkaEventException extends DomainException {
    public InvalidKafkaEventException(String message)
    {
        super(message);
    }
}
