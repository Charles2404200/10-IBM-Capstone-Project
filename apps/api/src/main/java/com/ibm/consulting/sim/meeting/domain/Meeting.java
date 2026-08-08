package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class Meeting extends BaseEntity {

    @Column(nullable = false)
    private UUID engagementId;

    @Column(nullable = false)
    private UUID personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status;

    private Instant completedAt;

    private String transcriptStorageReference;

    protected Meeting() {}

    public static Meeting start(UUID engagementId, UUID personaId) {
        Meeting m = new Meeting();
        m.engagementId = engagementId;
        m.personaId = personaId;
        m.status = MeetingStatus.IN_PROGRESS;
        return m;
    }

    public void complete() {
        this.status = MeetingStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void recordTranscriptExport(String storageReference) {
        this.transcriptStorageReference = storageReference;
    }

    public UUID getEngagementId() { return engagementId; }
    public UUID getPersonaId() { return personaId; }
    public MeetingStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public String getTranscriptStorageReference() { return transcriptStorageReference; }
}
