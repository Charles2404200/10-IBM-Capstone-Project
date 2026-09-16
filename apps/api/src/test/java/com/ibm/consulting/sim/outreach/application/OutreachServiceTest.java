package com.ibm.consulting.sim.outreach.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachOutcome;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutreachServiceTest {

    private static final String VALID_SUBJECT = "Modernising Example Co operations";
    private static final String VALID_BODY = """
            Hello Example Co team. Your distribution modernisation programme appears constrained by fragmented
            warehouse systems and manual exception handling. We have helped comparable organisations sequence
            this work without disrupting daily operations. Would you be available for one 30 minute meeting
            next week to discuss the operational evidence, validate priorities, and agree whether a focused
            discovery sprint would be useful?
            """.strip();

    @Test
    void firstAttemptPersistsAndDeterministicPolicyOwnsTheLifecycle() {
        Fixture fixture = fixture(new OutreachEvaluationResult(
                "The provider suggested rejection, but the quality scores are authoritative inputs.",
                "REJECTED", 100, 100, 100, 100, 0, 0));
        OutreachResponse response = fixture.service().send(
                fixture.engagement().getId(), fixture.userId(), VALID_SUBJECT, VALID_BODY);

        assertThat(fixture.attempts().findByEngagementId(fixture.engagement().getId())).hasSize(1);
        OutreachAttempt persisted = fixture.attempts().findByEngagementId(
                fixture.engagement().getId()).getFirst();
        assertThat(persisted.getAttemptNumber()).isEqualTo(1);
        assertThat(persisted.getSubject()).isEqualTo(VALID_SUBJECT);
        assertThat(persisted.getBody()).isEqualTo(VALID_BODY);
        assertThat(persisted.getOutcome()).isEqualTo(OutreachOutcome.ACCEPTED);
        assertThat(response.outcome()).isEqualTo("ACCEPTED");
        assertThat(fixture.engagement().getState()).isEqualTo(EngagementState.MEETING_SECURED);
        assertThat(fixture.engagement().getEvents()).extracting(event -> event.getState())
                .endsWith(EngagementState.OUTREACHING, EngagementState.MEETING_SECURED);
        verify(fixture.engagements()).findByIdAndUserIdForUpdate(
                fixture.engagement().getId(), fixture.userId());
    }

    @Test
    void replayingTheSameRequestIdReturnsTheExistingAttemptWithoutReevaluation() {
        Fixture fixture = fixture(OutreachEvaluationResult.safeFallback());

        OutreachResponse first = fixture.service().send(fixture.engagement().getId(), fixture.userId(),
                VALID_SUBJECT, VALID_BODY, "request-123");
        int eventsAfterFirstCall = fixture.engagement().getEvents().size();
        OutreachResponse replay = fixture.service().send(fixture.engagement().getId(), fixture.userId(),
                VALID_SUBJECT, VALID_BODY, "request-123");

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(fixture.attempts().findByEngagementId(fixture.engagement().getId())).hasSize(1);
        assertThat(fixture.engagement().getEvents()).hasSize(eventsAfterFirstCall);
        verify(fixture.ai(), times(1)).execute(eq("outreach_evaluation"),
                eq(fixture.engagement().getId()), anyString(), anyInt(), any(), any());
    }

    @Test
    void differentRequestIdsCreateDistinctLegitimateAttempts() {
        Fixture fixture = fixture(OutreachEvaluationResult.safeFallback());

        OutreachResponse first = fixture.service().send(fixture.engagement().getId(), fixture.userId(),
                VALID_SUBJECT, VALID_BODY, "request-123");
        OutreachResponse second = fixture.service().send(fixture.engagement().getId(), fixture.userId(),
                VALID_SUBJECT, VALID_BODY, "request-456");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(fixture.attempts().findByEngagementId(fixture.engagement().getId()))
                .extracting(OutreachAttempt::getAttemptNumber)
                .containsExactly(1, 2);
        verify(fixture.ai(), times(2)).execute(eq("outreach_evaluation"),
                eq(fixture.engagement().getId()), anyString(), anyInt(), any(), any());
    }

    private Fixture fixture(OutreachEvaluationResult evaluation) {
        UUID userId = UUID.randomUUID();
        Lead lead = Lead.create(UUID.randomUUID(), "Example Co", "Technology",
                "Distribution modernisation", LeadDifficulty.MEDIUM);
        Engagement engagement = Engagement.start(userId, lead.getScenarioId(), UUID.randomUUID());
        engagement.selectLead(lead.getId());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Research ready");
        EngagementRepository engagements = mock(EngagementRepository.class);
        when(engagements.findByIdAndUserIdForUpdate(engagement.getId(), userId))
                .thenReturn(Optional.of(engagement));
        LeadRepository leads = mock(LeadRepository.class);
        when(leads.findById(lead.getId())).thenReturn(Optional.of(lead));
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        ResearchEvidence research = mockEvidence(
                "Fragmented warehouse systems create manual exception handling");
        when(evidence.findByEngagementId(engagement.getId())).thenReturn(List.of(research));
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(engagement)).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.execute(eq("outreach_evaluation"), eq(engagement.getId()), anyString(), anyInt(), any(), any()))
                .thenReturn(evaluation);
        InMemoryOutreachRepository attempts = new InMemoryOutreachRepository();
        OutreachService service = new OutreachService(attempts, engagements, ai, new ObjectMapper(),
                difficulty, leads, evidence);
        return new Fixture(userId, engagement, service, attempts, engagements, ai);
    }

    private ResearchEvidence mockEvidence(String note) {
        ResearchEvidence evidence = mock(ResearchEvidence.class);
        when(evidence.getNote()).thenReturn(note);
        return evidence;
    }

    private record Fixture(UUID userId, Engagement engagement, OutreachService service,
                           InMemoryOutreachRepository attempts, EngagementRepository engagements,
                           AiOrchestrationService ai) {}

    private static final class InMemoryOutreachRepository implements OutreachRepository {
        private final List<OutreachAttempt> attempts = new ArrayList<>();

        @Override public OutreachAttempt save(OutreachAttempt attempt) {
            attempts.removeIf(existing -> existing.getId().equals(attempt.getId()));
            attempts.add(attempt);
            return attempt;
        }
        @Override public List<OutreachAttempt> findByEngagementId(UUID engagementId) {
            return attempts.stream().filter(attempt -> attempt.getEngagementId().equals(engagementId)).toList();
        }
        @Override public Optional<OutreachAttempt> findById(UUID id) {
            return attempts.stream().filter(attempt -> attempt.getId().equals(id)).findFirst();
        }
        @Override public int countByEngagementId(UUID engagementId) {
            return findByEngagementId(engagementId).size();
        }
    }
}
