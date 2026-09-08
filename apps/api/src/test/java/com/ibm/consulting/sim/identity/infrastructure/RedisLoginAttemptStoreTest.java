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

        store.recordFailure("account-hash");
        assertThat(store.failureCount("account-hash")).isEqualTo(2);
        store.reset("account-hash");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).execute(commands.capture());
        assertThat(commands.getAllValues().get(0))
                .startsWith("EVAL")
                .contains("1", "identity:login-failures:account-hash", "5", "900000");
        assertThat(commands.getAllValues().get(1))
                .containsExactly("GET", "identity:login-failures:account-hash");
        assertThat(commands.getAllValues().get(2))
                .containsExactly("DEL", "identity:login-failures:account-hash");
    }

    @Test
    void retainsBoundedLocalProtectionWhenRedisIsUnavailable() {
        UpstashRestClient client = mock(UpstashRestClient.class);
        when(client.execute(anyList())).thenThrow(new IllegalStateException("unavailable"));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(client, properties());

        store.recordFailure("account-hash");

        assertThat(store.failureCount("account-hash")).isEqualTo(1);
    }

    private LoginAttemptProperties properties() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(5);
        properties.setWindow(Duration.ofMinutes(15));
        properties.setMaximumTrackedAccounts(100);
        return properties;
    }
}
