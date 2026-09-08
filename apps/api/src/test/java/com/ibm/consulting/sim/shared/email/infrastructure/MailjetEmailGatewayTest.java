package com.ibm.consulting.sim.shared.email.infrastructure;

import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailjetEmailGatewayTest {

    @Test
    void mapsProviderNeutralEmailToMailjetSendPayload() {
        MailjetEmailProperties properties = new MailjetEmailProperties();
        properties.setFrom("sender@example.test");
        properties.setFromName("Consulting Simulation");
        properties.setReplyTo("support@example.test");
        OutboundEmail email = new OutboundEmail(
                "learner@example.test", "Confirm your email", "<p>Confirm</p>", "Confirm");

        Map<String, Object> payload = MailjetEmailGateway.payloadFor(email, properties);

        assertThat(payload).containsKey("Messages");
        Object rawMessages = payload.get("Messages");
        assertThat(rawMessages).isInstanceOf(List.class);
        List<?> messages = (List<?>) rawMessages;
        assertThat(messages).singleElement().isInstanceOf(Map.class);
        Map<?, ?> message = (Map<?, ?>) messages.getFirst();
        assertThat(message.get("Subject")).isEqualTo("Confirm your email");
        assertThat(message.get("TextPart")).isEqualTo("Confirm");
        assertThat(message.get("HTMLPart")).isEqualTo("<p>Confirm</p>");
        assertThat(message.get("From")).isEqualTo(Map.of(
                "Email", "sender@example.test", "Name", "Consulting Simulation"));
        assertThat(message.get("To")).isEqualTo(List.of(Map.of("Email", "learner@example.test")));
        assertThat(message.get("ReplyTo")).isEqualTo(Map.of("Email", "support@example.test"));
    }
}
