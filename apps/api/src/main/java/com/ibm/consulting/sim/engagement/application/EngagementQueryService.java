package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ibm.consulting.sim.shared.config.CacheConfig.ENGAGEMENT_DASHBOARD_CACHE;

/**
 * Read-model aggregator for the Command Centre "cockpit" view: stitches an
 * {@link Engagement} together with its scenario, selected lead, evidence
 * count, and active meeting id so the frontend receives a fully enriched
 * {@link EngagementResponse} without performing its own joins (same
 * read-model pattern as {@code PortfolioService}).
 */
@Service
public class EngagementQueryService {

    private final EngagementRepository engagementRepository;
    private final AssessmentRepository assessmentRepository;
    private final ScenarioRepository scenarioRepository;
    private final LeadRepository leadRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final MeetingRepository meetingRepository;

    public EngagementQueryService(EngagementRepository engagementRepository,
                                  AssessmentRepository assessmentRepository,
                                  ScenarioRepository scenarioRepository,
                                  LeadRepository leadRepository,
                                  ResearchEvidenceRepository evidenceRepository,
                                  MeetingRepository meetingRepository) {
        this.engagementRepository = engagementRepository;
        this.assessmentRepository = assessmentRepository;
        this.scenarioRepository = scenarioRepository;
        this.leadRepository = leadRepository;
        this.evidenceRepository = evidenceRepository;
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public EngagementResponse getWorkspace(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        return enrich(engagement);
    }

    /**
     * Cached command-centre projection. This is intentionally user-scoped:
     * it contains only the learner's own work and can be invalidated exactly
     * when a lifecycle command changes that work.
     */
    @Transactional
    @Cacheable(cacheNames = ENGAGEMENT_DASHBOARD_CACHE, key = "#userId")
    public List<EngagementResponse> listForUser(UUID userId) {
        List<Engagement> engagements = engagementRepository.findDashboardByUserId(userId);
        reconcileCompletedAssessments(engagements);
        return enrichAll(engagements);
    }

    /**
     * Repairs legacy runs whose assessment was persisted before the terminal
     * REVIEW -> COMPLETED transition existed. Keeping this at the read-model
     * boundary makes dashboards correct without requiring a data migration or
     * forcing the learner to revisit the assessment screen.
     */
    private void reconcileCompletedAssessments(List<Engagement> engagements) {
        List<Engagement> awaitingCompletion = engagements.stream()
                .filter(engagement -> engagement.getState() == EngagementState.REVIEW)
                .toList();
        if (awaitingCompletion.isEmpty()) return;

        Set<UUID> assessedEngagementIds = assessmentRepository.findAllByEngagementIdIn(
                        awaitingCompletion.stream().map(Engagement::getId).toList())
                .stream()
                .map(assessment -> assessment.getEngagementId())
                .collect(Collectors.toSet());

        awaitingCompletion.stream()
                .filter(engagement -> assessedEngagementIds.contains(engagement.getId()))
                .forEach(engagement -> {
                    engagement.transitionTo(EngagementState.COMPLETED,
                            "Recovered completed assessment lifecycle from dashboard");
                    engagementRepository.save(engagement);
                });
    }

    private EngagementResponse enrich(Engagement engagement) {
        Scenario scenario = scenarioRepository.findById(engagement.getScenarioId()).orElse(null);
        String leadCompanyName = null;
        if (engagement.getSelectedLeadId() != null) {
            leadCompanyName = leadRepository.findById(engagement.getSelectedLeadId())
                    .map(Lead::getCompanyName)
                    .orElse(null);
        }
        long evidenceCount = evidenceRepository.countByEngagementId(engagement.getId());
        UUID meetingId = meetingRepository.findByEngagementId(engagement.getId())
                .map(Meeting::getId)
                .orElse(null);
        return EngagementResponse.enrich(engagement,
                scenario != null ? scenario.getTitle() : null,
                scenario != null ? scenario.getIndustry() : null,
                leadCompanyName, evidenceCount, meetingId);
    }

    /** Uses set-based repository queries rather than four lookups per engagement. */
    private List<EngagementResponse> enrichAll(List<Engagement> engagements) {
        if (engagements.isEmpty()) return List.of();

        List<UUID> scenarioIds = engagements.stream().map(Engagement::getScenarioId).distinct().toList();
        List<UUID> engagementIds = engagements.stream().map(Engagement::getId).toList();
        List<UUID> leadIds = engagements.stream()
                .map(Engagement::getSelectedLeadId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Scenario> scenarios = byId(scenarioRepository.findByIdIn(scenarioIds), Scenario::getId);
        Map<UUID, Lead> leads = byId(leadRepository.findByIdIn(leadIds), Lead::getId);
        Map<UUID, Long> evidenceCounts = evidenceRepository.countByEngagementIds(engagementIds);
        Map<UUID, Meeting> latestMeetings = new HashMap<>();
        meetingRepository.findAllByEngagementIdIn(engagementIds).forEach(meeting ->
                latestMeetings.merge(meeting.getEngagementId(), meeting,
                        (current, candidate) -> current.getCreatedAt().isAfter(candidate.getCreatedAt()) ? current : candidate));

        return engagements.stream().map(engagement -> {
            Scenario scenario = scenarios.get(engagement.getScenarioId());
            Lead lead = leads.get(engagement.getSelectedLeadId());
            Meeting meeting = latestMeetings.get(engagement.getId());
            return EngagementResponse.enrich(engagement,
                    scenario != null ? scenario.getTitle() : null,
                    scenario != null ? scenario.getIndustry() : null,
                    lead != null ? lead.getCompanyName() : null,
                    evidenceCounts.getOrDefault(engagement.getId(), 0L),
                    meeting != null ? meeting.getId() : null);
        }).toList();
    }

    private static <T> Map<UUID, T> byId(Collection<T> source, java.util.function.Function<T, UUID> id) {
        Map<UUID, T> result = new HashMap<>();
        source.forEach(item -> result.put(id.apply(item), item));
        return result;
    }
}
