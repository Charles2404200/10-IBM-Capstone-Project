package com.ibm.consulting.sim.shared.email.infrastructure;

import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrevoEmailGatewayTest {

    @Test
    void mapsProviderNeutralEmailToBrevoTransactionalPayload() {
        BrevoEmailProperties properties = new BrevoEmailProperties();
        properties.setFrom("sender@example.test");
        properties.setFromName("Consulting Simulation");
        properties.setReplyTo("support@example.test");
        OutboundEmail email = new OutboundEmail(
                "learner@example.test", "Confirm your email", "<p>Confirm</p>", "Confirm");

        Map<String, Object> payload = BrevoEmailGateway.payloadFor(email, properties);

        assertThat(payload).containsEntry("subject", "Confirm your email")
                .containsEntry("htmlContent", "<p>Confirm</p>")
                .containsEntry("textContent", "Confirm");
        assertThat(payload.get("sender")).isEqualTo(Map.of(
                "email", "sender@example.test", "name", "Consulting Simulation"));
        assertThat(payload.get("to")).isEqualTo(List.of(Map.of("email", "learner@example.test")));
        assertThat(payload.get("replyTo")).isEqualTo(Map.of("email", "support@example.test"));
    }
}
