package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    private final ScenarioRepository scenarioRepository;
    private final LeadRepository leadRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final MeetingRepository meetingRepository;

    public EngagementQueryService(EngagementRepository engagementRepository,
                                  ScenarioRepository scenarioRepository,
                                  LeadRepository leadRepository,
                                  ResearchEvidenceRepository evidenceRepository,
                                  MeetingRepository meetingRepository) {
        this.engagementRepository = engagementRepository;
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

    @Transactional(readOnly = true)
    public List<EngagementResponse> listForUser(UUID userId) {
        return engagementRepository.findByUserId(userId).stream()
                .map(this::enrich)
                .toList();
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
}
