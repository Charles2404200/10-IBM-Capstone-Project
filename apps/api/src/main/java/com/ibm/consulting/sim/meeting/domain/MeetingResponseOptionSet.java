package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stable guided responses for one client turn. Persisting them prevents a refresh
 * or reconnect from quietly changing the learner's available decisions.
 */
@Entity
@Table(name = "meeting_response_option_sets", uniqueConstraints =
        @UniqueConstraint(name = "uk_meeting_response_options_source", columnNames = {"meeting_id", "source_sequence"}))
public class MeetingResponseOptionSet extends BaseEntity {

    @Column(nullable = false)
    private UUID meetingId;

    @Column(nullable = false)
    private int sourceSequence;

    @ElementCollection
    @CollectionTable(name = "meeting_response_option_values", joinColumns = @JoinColumn(name = "option_set_id"))
    @OrderColumn(name = "option_position")
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private List<String> options = new ArrayList<>();

    protected MeetingResponseOptionSet() {}

    public static MeetingResponseOptionSet generated(UUID meetingId, int sourceSequence, List<String> options) {
        MeetingResponseOptionSet set = new MeetingResponseOptionSet();
        set.meetingId = meetingId;
        set.sourceSequence = sourceSequence;
        set.options = new ArrayList<>(options);
        return set;
    }

    public UUID getMeetingId() { return meetingId; }
    public int getSourceSequence() { return sourceSequence; }
    public List<String> getOptions() { return List.copyOf(options); }
}
