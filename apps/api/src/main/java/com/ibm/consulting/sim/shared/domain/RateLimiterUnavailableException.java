package com.ibm.consulting.sim.shared.domain;

public class RateLimiterUnavailableException extends DomainException{
    public RateLimiterUnavailableException(){
        super("Rate limiting service temporarily unavailable");
    }
}
