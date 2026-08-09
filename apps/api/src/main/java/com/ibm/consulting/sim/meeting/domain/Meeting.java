package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @Enumerated(EnumType.STRING)
    private MeetingCompletionOutcome completionOutcome;

    @Enumerated(EnumType.STRING)
    private MeetingTerminationReason terminationReason;

    @Column(columnDefinition = "text")
    private String terminationMessage;

    @Column(columnDefinition = "text")
    private String debriefFeedback;

    @ElementCollection
    @CollectionTable(name = "meeting_debrief_tips", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "tip", columnDefinition = "text")
    @OrderColumn(name = "tip_order")
    private List<String> debriefTips = new ArrayList<>();

    protected Meeting() {}

    public static Meeting start(UUID engagementId, UUID personaId) {
        Meeting m = new Meeting();
        m.engagementId = engagementId;
        m.personaId = personaId;
        m.status = MeetingStatus.IN_PROGRESS;
        return m;
    }

    public void complete(MeetingCompletionOutcome completionOutcome, String debriefFeedback, List<String> debriefTips) {
        complete(completionOutcome, debriefFeedback, debriefTips, null);
    }

    public void complete(MeetingCompletionOutcome completionOutcome, String debriefFeedback, List<String> debriefTips,
                         MeetingTerminationReason terminationReason) {
        this.status = MeetingStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.completionOutcome = completionOutcome;
        this.debriefFeedback = debriefFeedback;
        this.terminationReason = terminationReason;
        this.terminationMessage = terminationReason == null ? null : debriefFeedback;
        this.debriefTips.clear();
        this.debriefTips.addAll(debriefTips);
    }

    public void recordTranscriptExport(String storageReference) {
        this.transcriptStorageReference = storageReference;
    }

    public UUID getEngagementId() { return engagementId; }
    public UUID getPersonaId() { return personaId; }
    public MeetingStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public String getTranscriptStorageReference() { return transcriptStorageReference; }
    public MeetingCompletionOutcome getCompletionOutcome() { return completionOutcome; }
    public String getDebriefFeedback() { return debriefFeedback; }
    public List<String> getDebriefTips() { return Collections.unmodifiableList(debriefTips); }
    public MeetingTerminationReason getTerminationReason() { return terminationReason; }
    public String getTerminationMessage() { return terminationMessage; }
}
