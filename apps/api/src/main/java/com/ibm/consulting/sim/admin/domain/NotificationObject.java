package com.ibm.consulting.sim.admin.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.UUID;

public class NotificationObject {

    private final UUID eventId;
    private final UUID userId;
    private final String topicName;
    private final String message;
    private final UserRole role;

    @JsonCreator
    public NotificationObject(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("topicName") String topicName,
            @JsonProperty("message") String message,
            @JsonProperty("role") UserRole role) {
        this.eventId = eventId;
        this.userId = userId;
        this.topicName = topicName;
        this.message = message;
        this.role = role;
    }

    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public String getTopicName() { return topicName; }
    public String getMessage() { return message; }
    public UserRole getRole() { return role; }
}
