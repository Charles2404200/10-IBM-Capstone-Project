package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class NotificationReadException extends DomainException {
    public NotificationReadException(String message)
    {
        super(message);
    }
}
