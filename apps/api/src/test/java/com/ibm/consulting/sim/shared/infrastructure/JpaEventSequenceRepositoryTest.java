package com.ibm.consulting.sim.shared.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaEventSequenceRepositoryTest {

    private final SpringDataEventSequenceRepository springDataRepository =
            mock(SpringDataEventSequenceRepository.class);
    private final JpaEventSequenceRepository repository =
            new JpaEventSequenceRepository(springDataRepository);

    @Test
    void returnsSequenceAllocatedByDatabase() {
        when(springDataRepository.allocateNext("notification:learner"))
                .thenReturn(4L);

        long sequence = repository.next("notification:learner");

        assertEquals(4L, sequence);
    }

    @Test
    void failsWhenDatabaseDoesNotReturnSequence() {
        when(springDataRepository.allocateNext("notification:reviewer"))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> repository.next("notification:reviewer")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankOrderingKey(String orderingKey) {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.next(orderingKey)
        );

        verifyNoInteractions(springDataRepository);
    }

    @Test
    void rejectsOrderingKeyLongerThanDatabaseColumn() {
        String orderingKey = "x".repeat(256);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.next(orderingKey)
        );

        verifyNoInteractions(springDataRepository);
    }
}
