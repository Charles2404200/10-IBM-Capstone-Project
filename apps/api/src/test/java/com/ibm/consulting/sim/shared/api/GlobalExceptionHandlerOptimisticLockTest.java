package com.ibm.consulting.sim.shared.api;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerOptimisticLockTest {

    @Test
    void optimisticConflictMapsToSafeConcurrentUpdateProblem() {
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException(Engagement.class, UUID.randomUUID());

        ProblemDetail detail = new GlobalExceptionHandler().handleOptimisticLock(exception);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getType()).isEqualTo(
                URI.create("https://consulting-sim.ibm.com/problems/concurrent-update"));
        assertThat(detail.getDetail()).isEqualTo(
                "This item was updated at the same time. Please retry the save.");
        assertThat(detail.toString()).doesNotContain("SQL", "Hibernate", "stack", "ObjectOptimisticLockingFailureException");
    }
}
