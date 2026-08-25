package com.ibm.consulting.sim.shared.email.infrastructure;

import com.ibm.consulting.sim.shared.email.application.EmailDeliveryGateway;
import com.ibm.consulting.sim.shared.email.application.EmailDeliveryUnavailableException;
import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Production adapter for Resend's transactional-email API. */
@Component
public class ResendEmailGateway implements EmailDeliveryGateway {

    private final ResendEmailProperties properties;
    private final RestClient restClient;

    public ResendEmailGateway(ResendEmailProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public void send(OutboundEmail email) {
        if (properties.getApiKey().isBlank()) {
            throw new EmailDeliveryUnavailableException("Email delivery is not configured. Set RESEND_API_KEY.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", properties.getFrom());
        payload.put("to", new String[]{email.recipient()});
        payload.put("subject", email.subject());
        payload.put("html", email.html());
        payload.put("text", email.text());
        if (!properties.getReplyTo().isBlank()) {
            payload.put("reply_to", properties.getReplyTo());
        }
        try {
            restClient.post().uri("/emails")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new EmailDeliveryUnavailableException("Email delivery is temporarily unavailable.", ex);
        }
    }
}
