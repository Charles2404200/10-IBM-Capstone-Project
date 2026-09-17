package com.ibm.consulting.sim.identity.infrastructure;

import com.fasterxml.jackson.databind.node.IntNode;
import com.ibm.consulting.sim.identity.application.LoginAttemptProperties;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginAttemptStoreTest {

    @Test
    void usesAtomicCappedCounterWithExpiryAndNamespacedKey() {
        UpstashRestClient client = mock(UpstashRestClient.class);
        when(client.execute(anyList())).thenReturn(IntNode.valueOf(2));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(client, properties());

        assertThat(store.tryAcquire("account-hash")).isTrue();
        assertThat(store.failureCount("account-hash")).isEqualTo(2);
        store.release("account-hash");
        store.reset("account-hash");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.forClass(List.class);
        verify(client, times(4)).execute(commands.capture());
        assertThat(commands.getAllValues().get(0))
                .startsWith("EVAL")
                .contains("1", "identity:login-failures:account-hash", "5", "900000");
        assertThat(commands.getAllValues().get(1))
                .containsExactly("GET", "identity:login-failures:account-hash");
        assertThat(commands.getAllValues().get(2))
                .startsWith("EVAL")
                .contains("1", "identity:login-failures:account-hash");
        assertThat(commands.getAllValues().get(3))
                .containsExactly("DEL", "identity:login-failures:account-hash");
    }

    @Test
    void retainsBoundedLocalProtectionWhenRedisIsUnavailable() {
        UpstashRestClient client = mock(UpstashRestClient.class);
        when(client.execute(anyList())).thenThrow(new IllegalStateException("unavailable"));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(client, properties());

        assertThat(store.tryAcquire("account-hash")).isTrue();

        assertThat(store.failureCount("account-hash")).isEqualTo(1);
    }

    @Test
    void healthyRedisAttemptsRemainInTheLocalShadowDuringAnOutage() {
        UpstashRestClient client = mock(UpstashRestClient.class);
        when(client.execute(anyList()))
                .thenReturn(IntNode.valueOf(1), IntNode.valueOf(2))
                .thenThrow(new IllegalStateException("outage"));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(client, properties());

        assertThat(store.tryAcquire("account-hash")).isTrue();
        assertThat(store.tryAcquire("account-hash")).isTrue();

        assertThat(store.failureCount("account-hash")).isEqualTo(2);
    }

    @Test
    void deniedRedisAdmissionRetainsTheThresholdDuringAnOutage() {
        UpstashRestClient client = mock(UpstashRestClient.class);
        when(client.execute(anyList()))
                .thenReturn(IntNode.valueOf(-5))
                .thenThrow(new IllegalStateException("outage"));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(client, properties());

        assertThat(store.tryAcquire("account-hash")).isFalse();

        assertThat(store.failureCount("account-hash")).isEqualTo(5);
    }

    @Test
    void successfulResetClearsTheLocalShadowAndRedisCounter() {
        UpstashRestClient client = mock(UpstashRestClient.class);
        when(client.execute(anyList()))
                .thenReturn(IntNode.valueOf(3), IntNode.valueOf(1))
                .thenThrow(new IllegalStateException("outage"));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(client, properties());
        assertThat(store.tryAcquire("account-hash")).isTrue();

        store.reset("account-hash");

        assertThat(store.failureCount("account-hash")).isZero();
    }

    private LoginAttemptProperties properties() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(5);
        properties.setWindow(Duration.ofMinutes(15));
        properties.setMaximumTrackedAccounts(100);
        return properties;
    }
}
