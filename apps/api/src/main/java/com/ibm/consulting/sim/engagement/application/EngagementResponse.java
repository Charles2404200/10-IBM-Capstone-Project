package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementEvent;
import com.ibm.consulting.sim.engagement.domain.EngagementProgressCalculator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EngagementResponse(
        UUID id,
        UUID userId,
        UUID scenarioId,
        UUID personaId,
        String state,
        UUID selectedLeadId,
        Instant createdAt,
        Instant completedAt,
        List<EventRecord> events,
        // ─── Cockpit enrichment: read-model fields stitched from other modules
        // (scenario/lead/evidence) so the frontend never has to join data itself. ───
        String scenarioTitle,
        String scenarioIndustry,
        String leadCompanyName,
        String phase,
        String phaseLabel,
        int progressPercent,
        String nextAction,
        long evidenceCount,
        long daysElapsed,
        UUID meetingId) {

    public record EventRecord(UUID id, String state, String description, Instant occurredAt) {
        static EventRecord from(EngagementEvent e) {
            return new EventRecord(e.getId(), e.getState().name(), e.getDescription(), e.getOccurredAt());
        }
    }

    /** Minimal projection used by list views before enrichment data is available (e.g. tests). */
    public static EngagementResponse from(Engagement e) {
        return enrich(e, null, null, null, 0, null);
    }

    public static EngagementResponse enrich(Engagement e, String scenarioTitle, String scenarioIndustry,
                                            String leadCompanyName, long evidenceCount, UUID meetingId) {
        var phase = EngagementProgressCalculator.phaseOf(e.getState());
        long daysElapsed = Duration.between(e.getCreatedAt(), Instant.now()).toDays();
        return new EngagementResponse(
                e.getId(), e.getUserId(), e.getScenarioId(), e.getPersonaId(),
                e.getState().name(), e.getSelectedLeadId(),
                e.getCreatedAt(), e.getCompletedAt(),
                e.getEvents().stream().map(EventRecord::from).toList(),
                scenarioTitle, scenarioIndustry, leadCompanyName,
                phase.name(), phase.label(),
                EngagementProgressCalculator.progressPercent(e.getState()),
                EngagementProgressCalculator.nextAction(e.getState()),
                evidenceCount, daysElapsed, meetingId);
    }
}
