package com.ibm.consulting.sim.shared.domain;

public class RateLimitExceededException extends DomainException {
    public RateLimitExceededException(){
        super("Max Rate Limit Reached");
    }
}
