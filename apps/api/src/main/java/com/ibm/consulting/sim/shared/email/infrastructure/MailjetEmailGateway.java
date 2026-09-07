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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTPS transactional-email adapter for Mailjet's v3.1 Send API. */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "mailjet")
public class MailjetEmailGateway implements EmailDeliveryGateway {

    private final MailjetEmailProperties properties;
    private final RestClient restClient;

    public MailjetEmailGateway(MailjetEmailProperties properties) {
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
            restClient.post().uri("/send")
                    .headers(headers -> headers.setBasicAuth(
                            properties.getApiKey(), properties.getSecretKey(), StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadFor(email, properties))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new EmailDeliveryUnavailableException("Email delivery is temporarily unavailable.", ex);
        }
    }

    static Map<String, Object> payloadFor(OutboundEmail email, MailjetEmailProperties properties) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("From", participant(properties.getFrom(), properties.getFromName()));
        message.put("To", List.of(participant(email.recipient(), "")));
        message.put("Subject", email.subject());
        message.put("TextPart", email.text());
        message.put("HTMLPart", email.html());
        if (!properties.getReplyTo().isBlank()) {
            message.put("ReplyTo", participant(properties.getReplyTo(), ""));
        }
        return Map.of("Messages", List.of(message));
    }

    private static Map<String, String> participant(String email, String name) {
        Map<String, String> participant = new LinkedHashMap<>();
        participant.put("Email", email);
        if (!name.isBlank()) {
            participant.put("Name", name);
        }
        return participant;
    }

    private void validateConfiguration() {
        if (properties.getApiKey().isBlank() || properties.getSecretKey().isBlank() || properties.getFrom().isBlank()) {
            throw new EmailDeliveryUnavailableException(
                    "Email delivery is not configured. Set MAILJET_API_KEY, MAILJET_SECRET_KEY and MAILJET_FROM.");
        }
    }
}
