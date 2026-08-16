package com.ibm.consulting.sim.shared.domain;

import java.time.Duration;

public interface RateLimiterService<P  extends  Enum<P> & RateLimiterFailurePolicy>  {

    boolean tryAcquire(
            String key,
            int maxRequests,
            Duration window,
            P failurePolicy
    );
}
