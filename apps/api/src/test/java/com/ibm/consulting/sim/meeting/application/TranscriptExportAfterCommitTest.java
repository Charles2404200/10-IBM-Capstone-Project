package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingCompletionOutcome;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.shared.domain.ObjectStorageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptExportAfterCommitTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rolledBackCompletionNeverTouchesObjectStorage() {
        Fixture fixture = fixture();
        TransactionSynchronizationManager.initSynchronization();

        fixture.service().scheduleAfterCommit(fixture.meeting().getId());
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fixture.storage(), never()).upload(anyString(), any(), anyString());
        verify(fixture.meetings(), never()).save(any());
    }

    @Test
    void successfulCompletionExportsOnlyAfterCommitAndPersistsTheReference() {
        Fixture fixture = fixture();
        when(fixture.storage().upload(anyString(), any(), eq("application/json")))
                .thenReturn("storage://transcript");
        TransactionSynchronizationManager.initSynchronization();

        fixture.service().scheduleAfterCommit(fixture.meeting().getId());
        verify(fixture.storage(), never()).upload(anyString(), any(), anyString());
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        callbacks.forEach(TransactionSynchronization::afterCommit);

        verify(fixture.storage()).upload(
                eq("transcripts/%s/%s.json".formatted(
                        fixture.meeting().getEngagementId(), fixture.meeting().getId())),
                any(), eq("application/json"));
        verify(fixture.meetings()).save(fixture.meeting());
        assertThat(fixture.meeting().getTranscriptStorageReference()).isEqualTo("storage://transcript");
    }

    @Test
    void retryUsesTheDeterministicKeyAndDoesNotUploadAnExistingExportAgain() {
        Fixture fixture = fixture();
        when(fixture.storage().upload(anyString(), any(), anyString())).thenReturn("storage://transcript");

        runAfterCommit(() -> fixture.service().scheduleAfterCommit(fixture.meeting().getId()));
        runAfterCommit(() -> fixture.service().scheduleAfterCommit(fixture.meeting().getId()));

        verify(fixture.storage(), times(1)).upload(anyString(), any(), anyString());
    }

    private void runAfterCommit(Runnable registration) {
        TransactionSynchronizationManager.initSynchronization();
        registration.run();
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        callbacks.forEach(TransactionSynchronization::afterCommit);
    }

    private Fixture fixture() {
        ObjectStorageClient storage = mock(ObjectStorageClient.class);
        ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
        when(turns.findByMeetingIdOrderBySequenceAsc(any())).thenReturn(List.of());
        MeetingRepository meetings = mock(MeetingRepository.class);
        Meeting meeting = Meeting.start(UUID.randomUUID(), UUID.randomUUID());
        meeting.complete(MeetingCompletionOutcome.PASSED, "Complete", List.of("Keep listening"));
        when(meetings.findByIdForUpdate(meeting.getId())).thenReturn(Optional.of(meeting));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TranscriptExportService service = new TranscriptExportService(
                storage, turns, meetings, new ObjectMapper(), transactionManager);
        return new Fixture(service, storage, meetings, meeting);
    }

    private record Fixture(TranscriptExportService service, ObjectStorageClient storage,
                           MeetingRepository meetings, Meeting meeting) {}
}
