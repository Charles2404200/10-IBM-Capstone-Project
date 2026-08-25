package com.ibm.consulting.sim.shared.domain;

/**
 * Allocates monotonically increasing sequence numbers within an ordering key.
 *
 * <p>The persistence adapter must perform allocation atomically so concurrent
 * callers cannot receive the same value.</p>
 */
public interface EventSequenceRepository {

    long next(String orderingKey);
}
