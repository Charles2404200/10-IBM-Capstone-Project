package com.ibm.consulting.sim.shared.domain;

public class NotFoundException extends DomainException {

    public NotFoundException(String resourceType, Object id) {
        super("%s not found: %s".formatted(resourceType, id));
    }
}
