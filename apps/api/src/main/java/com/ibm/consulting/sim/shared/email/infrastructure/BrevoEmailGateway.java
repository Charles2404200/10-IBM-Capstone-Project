package com.ibm.consulting.sim.shared.email.infrastructure;

import com.ibm.consulting.sim.shared.email.application.EmailDeliveryGateway;
import com.ibm.consulting.sim.shared.email.application.EmailDeliveryUnavailableException;
import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTPS transactional-email adapter for Brevo.
 *
 * <p>Unlike SMTP, this uses outbound HTTPS and is suitable for hosts that block
 * direct SMTP connections. Brevo verifies the configured sender before delivery.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "brevo")
public class BrevoEmailGateway implements EmailDeliveryGateway {

    private final BrevoEmailProperties properties;
    private final RestClient restClient;

    public BrevoEmailGateway(BrevoEmailProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public void send(OutboundEmail email) {
        validateConfiguration();
        try {
            restClient.post().uri("/v3/smtp/email")
                    .header("api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadFor(email, properties))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new EmailDeliveryUnavailableException("Email delivery is temporarily unavailable.", ex);
        }
    }

    static Map<String, Object> payloadFor(OutboundEmail email, BrevoEmailProperties properties) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, String> sender = new LinkedHashMap<>();
        sender.put("email", properties.getFrom());
        if (!properties.getFromName().isBlank()) {
            sender.put("name", properties.getFromName());
        }
        payload.put("sender", sender);
        payload.put("to", List.of(Map.of("email", email.recipient())));
        payload.put("subject", email.subject());
        payload.put("htmlContent", email.html());
        payload.put("textContent", email.text());
        if (!properties.getReplyTo().isBlank()) {
            payload.put("replyTo", Map.of("email", properties.getReplyTo()));
        }
        return payload;
    }

    private void validateConfiguration() {
        if (properties.getApiKey().isBlank() || properties.getFrom().isBlank()) {
            throw new EmailDeliveryUnavailableException(
                    "Email delivery is not configured. Set BREVO_API_KEY and BREVO_FROM.");
        }
    }
}
