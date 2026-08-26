package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.outbox.EventSequence;
import com.ibm.consulting.sim.shared.domain.outbox.EventSequenceRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
interface SpringDataEventSequenceRepository
        extends JpaRepository<EventSequence, String> {

    /**
     * The data-changing CTE lets PostgreSQL allocate and return the next value
     * in one atomic statement while exposing it to Spring Data as one scalar
     * query result.
     */
    @Query(value = """
            WITH allocated_sequence AS (
                INSERT INTO event_sequence
                    (ordering_key, current_value)
                VALUES
                    (:orderingKey, 1)
                ON CONFLICT (ordering_key)
                DO UPDATE
                    SET current_value = event_sequence.current_value + 1
                RETURNING current_value
            )
            SELECT current_value
            FROM allocated_sequence
            """, nativeQuery = true)
    Long allocateNext(@Param("orderingKey") String orderingKey);
}

@Repository
public class JpaEventSequenceRepository implements EventSequenceRepository {

    private static final int MAX_ORDERING_KEY_LENGTH = 255;

    private final SpringDataEventSequenceRepository repository;

    public JpaEventSequenceRepository(SpringDataEventSequenceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public long next(String orderingKey) {
        validateOrderingKey(orderingKey);

        Long value = repository.allocateNext(orderingKey);
        if (value == null) {
            throw new IllegalStateException(
                    "Could not allocate an event sequence for ordering key"
            );
        }

        return value;
    }

    private static void validateOrderingKey(String orderingKey) {
        if (orderingKey == null || orderingKey.isBlank()) {
            throw new IllegalArgumentException("orderingKey must not be blank");
        }

        if (orderingKey.length() > MAX_ORDERING_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "orderingKey must not exceed " + MAX_ORDERING_KEY_LENGTH + " characters"
            );
        }
    }
}
