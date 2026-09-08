package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
import com.ibm.consulting.sim.shared.domain.ObjectStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exports a completed meeting's transcript to object storage (§3.2, §8 Phase 3).
 * Keeps the relational store free of large blobs while preserving a durable,
 * exportable record for review and compliance.
 */
@Component
class TranscriptExportService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptExportService.class);

    private final ObjectStorageClient objectStorageClient;
    private final ConversationTurnRepository turnRepository;
    private final MeetingRepository meetingRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate exportTransaction;

    TranscriptExportService(ObjectStorageClient objectStorageClient,
                             ConversationTurnRepository turnRepository,
                             MeetingRepository meetingRepository,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.objectStorageClient = objectStorageClient;
        this.turnRepository = turnRepository;
        this.meetingRepository = meetingRepository;
        this.objectMapper = objectMapper;
        this.exportTransaction = new TransactionTemplate(transactionManager);
        this.exportTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Defers the external upload until the meeting-completion transaction is durable. */
    void scheduleAfterCommit(UUID meetingId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            exportBestEffort(meetingId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                exportBestEffort(meetingId);
            }
        });
    }

    private void exportBestEffort(UUID meetingId) {
        try {
            exportTransaction.executeWithoutResult(ignored -> exportAndPersist(meetingId));
        } catch (RuntimeException exception) {
            log.error("Transcript export failed for completed meeting {}; retaining relational transcript",
                    meetingId, exception);
        }
    }

    private void exportAndPersist(UUID meetingId) {
        meetingRepository.findByIdForUpdate(meetingId)
                .filter(meeting -> meeting.getStatus() == MeetingStatus.COMPLETED)
                .filter(meeting -> meeting.getTranscriptStorageReference() == null
                        || meeting.getTranscriptStorageReference().isBlank())
                .ifPresent(meeting -> {
                    String storageReference = export(meeting);
                    if (storageReference != null && !storageReference.isBlank()) {
                        meeting.recordTranscriptExport(storageReference);
                        meetingRepository.save(meeting);
                    }
                });
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
