package com.ibm.consulting.sim.meeting.infrastructure.realtime;

import com.ibm.consulting.sim.shared.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingRealtimeContractTest {

    @Test
    void websocketEndpointAndBrokerPrefixesMatchTheFrontendContract() {
        CorsProperties cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("https://web.example.test"));
        WebSocketConfig config = new WebSocketConfig(
                mock(StompAuthChannelInterceptor.class), mock(MeetingSubscriptionInterceptor.class), cors);
        StompEndpointRegistry endpoints = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(endpoints.addEndpoint("/ws")).thenReturn(endpoint);
        when(endpoint.setAllowedOriginPatterns(any(String[].class))).thenReturn(endpoint);
        MessageBrokerRegistry broker = mock(MessageBrokerRegistry.class);

        config.registerStompEndpoints(endpoints);
        config.configureMessageBroker(broker);

        verify(endpoints).addEndpoint("/ws");
        verify(endpoint).setAllowedOriginPatterns("https://web.example.test");
        verify(broker).enableSimpleBroker("/topic");
        verify(broker).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void messageDestinationAndPayloadMatchTheFrontendPublisher() throws Exception {
        var method = MeetingSocketController.class.getMethod(
                "sendMessage", UUID.class, MeetingSocketController.MeetingMessage.class, Principal.class);
        MessageMapping mapping = method.getAnnotation(MessageMapping.class);
        var payload = new MeetingSocketController.MeetingMessage("Question", "message-1");

        assertThat(mapping.value()).containsExactly("/meetings/{meetingId}/send");
        assertThat(payload.message()).isEqualTo("Question");
        assertThat(payload.messageId()).isEqualTo("message-1");
    }
}
