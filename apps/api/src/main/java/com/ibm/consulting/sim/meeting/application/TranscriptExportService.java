package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.shared.domain.ObjectStorageClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Exports a completed meeting's transcript to object storage (§3.2, §8 Phase 3).
 * Keeps the relational store free of large blobs while preserving a durable,
 * exportable record for review and compliance.
 */
@Component
class TranscriptExportService {

    private final ObjectStorageClient objectStorageClient;
    private final ConversationTurnRepository turnRepository;
    private final ObjectMapper objectMapper;

    TranscriptExportService(ObjectStorageClient objectStorageClient,
                             ConversationTurnRepository turnRepository,
                             ObjectMapper objectMapper) {
        this.objectStorageClient = objectStorageClient;
        this.turnRepository = turnRepository;
        this.objectMapper = objectMapper;
    }

    String export(Meeting meeting) {
        List<ConversationTurn> turns = turnRepository.findByMeetingIdOrderBySequenceAsc(meeting.getId());
        List<Map<String, Object>> payload = turns.stream()
                .map(t -> Map.<String, Object>of(
                        "sequence", t.getSequence(),
                        "actor", t.getActor().name(),
                        "content", t.getContent(),
                        "signals", t.getSignals() == null ? "" : t.getSignals(),
                        "occurredAt", t.getCreatedAt().toString()))
                .toList();

        Map<String, Object> transcript = Map.of(
                "meetingId", meeting.getId().toString(),
                "engagementId", meeting.getEngagementId().toString(),
                "exportedAt", Instant.now().toString(),
                "turns", payload);

        try {
            byte[] content = objectMapper.writeValueAsBytes(transcript);
            String key = "transcripts/%s/%s.json".formatted(meeting.getEngagementId(), meeting.getId());
            return objectStorageClient.upload(key, content, "application/json");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise transcript export for meeting " + meeting.getId(), e);
        }
    }
}
