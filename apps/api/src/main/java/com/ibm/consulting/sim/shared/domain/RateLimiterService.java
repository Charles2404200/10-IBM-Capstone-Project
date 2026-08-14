package com.ibm.consulting.sim.shared.domain;

import java.time.Duration;

public interface RateLimiterService {

    boolean tryAcquire(
            String key,
            int maxRequests,
            Duration window
    );
}
