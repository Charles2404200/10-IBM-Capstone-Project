package com.ibm.consulting.sim.engagement.infrastructure;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.engagement.domain.LifecycleFactRepository;
import com.ibm.consulting.sim.engagement.domain.LifecycleFacts;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchReadinessPolicy;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparation;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.outreach.domain.CapabilityBrief;
import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachOutcome;
import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ProposalStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loads lifecycle facts with a fixed number of set-based queries for both commands and dashboards. */
@Repository
public class JpaLifecycleFactRepository implements LifecycleFactRepository {
    private final EntityManager entityManager;

    public JpaLifecycleFactRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LifecycleFacts> loadAll(List<UUID> engagementIds) {
        if (engagementIds == null || engagementIds.isEmpty()) return Map.of();
        List<UUID> ids = engagementIds.stream().distinct().toList();
        var evidence = group(entityManager.createQuery("select distinct item from ResearchEvidence item "
                        + "left join fetch item.supportingEvidenceIds where item.engagementId in :ids", ResearchEvidence.class)
                .setParameter("ids", ids).getResultList(), ResearchEvidence::getEngagementId);
        var outreach = group(query(OutreachAttempt.class, ids), OutreachAttempt::getEngagementId);
        var briefs = group(query(CapabilityBrief.class, ids), CapabilityBrief::getEngagementId);
        var preparations = latest(query(MeetingPreparation.class, ids), MeetingPreparation::getEngagementId);
        Map<UUID, Meeting> meetings = new HashMap<>();
        query(Meeting.class, ids).forEach(meeting -> meetings.merge(meeting.getEngagementId(), meeting,
                (current, candidate) -> current.getCreatedAt().isAfter(candidate.getCreatedAt()) ? current : candidate));
        var personaStates = latest(query(PersonaState.class, ids), PersonaState::getEngagementId);
        var proposals = latest(query(Proposal.class, ids), Proposal::getEngagementId);
        var assessments = latest(query(Assessment.class, ids), Assessment::getEngagementId);

        Map<UUID, LifecycleFacts> result = new HashMap<>();
        for (UUID id : ids) {
            List<ResearchEvidence> research = evidence.getOrDefault(id, List.of());
            MeetingPreparation preparation = preparations.get(id);
            Meeting meeting = meetings.get(id);
            PersonaState persona = personaStates.get(id);
            Proposal proposal = proposals.get(id);
            boolean accepted = outreach.getOrDefault(id, List.of()).stream()
                    .anyMatch(item -> item.getOutcome() == OutreachOutcome.ACCEPTED)
                    || briefs.getOrDefault(id, List.of()).stream()
                    .anyMatch(item -> item.getOutcome() == OutreachOutcome.ACCEPTED);
            result.put(id, new LifecycleFacts(false, ResearchReadinessPolicy.evidenceCount(research),
                    ResearchReadinessPolicy.hasHypothesis(research),
                    research.isEmpty() ? null : ResearchReadinessPolicy.confidencePercent(research), accepted,
                    preparation != null && preparation.isReady(), preparation == null ? null : preparation.getReadinessScore(),
                    meeting != null, meeting != null && meeting.getStatus() == MeetingStatus.COMPLETED,
                    persona == null ? null : persona.getTrust(), persona == null ? null : persona.getInterest(),
                    persona == null ? null : persona.getPatience(), proposal != null,
                    proposal != null && proposal.getStatus() == ProposalStatus.SUBMITTED,
                    proposal != null && proposal.getClientDecisionOutcome() != null, assessments.containsKey(id)));
        }
        return Map.copyOf(result);
    }

    private <T> List<T> query(Class<T> type, List<UUID> ids) {
        return entityManager.createQuery("select item from " + type.getSimpleName()
                        + " item where item.engagementId in :ids", type)
                .setParameter("ids", ids).getResultList();
    }

    private static <T> Map<UUID, List<T>> group(List<T> values, java.util.function.Function<T, UUID> id) {
        Map<UUID, List<T>> result = new HashMap<>();
        values.forEach(value -> result.computeIfAbsent(id.apply(value), ignored -> new ArrayList<>()).add(value));
        return result;
    }

    private static <T> Map<UUID, T> latest(List<T> values, java.util.function.Function<T, UUID> id) {
        Map<UUID, T> result = new HashMap<>();
        values.forEach(value -> result.put(id.apply(value), value));
        return result;
    }
}
