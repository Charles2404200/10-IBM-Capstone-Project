package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.application.LoginAttemptProperties;
import com.ibm.consulting.sim.identity.application.LoginAttemptStore;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisLoginAttemptStoreWiringTest {

    @Test
    void upstashStoreCanBeCreatedBySpringUsingItsRuntimeDependencies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("app.cache.provider=upstash").applyTo(context);
            context.registerBean(UpstashRestClient.class, () -> mock(UpstashRestClient.class));
            context.registerBean(LoginAttemptProperties.class, LoginAttemptProperties::new);
            context.register(RedisLoginAttemptStore.class);

            context.refresh();

            assertThat(context.getBean(LoginAttemptStore.class))
                    .isInstanceOf(RedisLoginAttemptStore.class);
        }
    }
}
