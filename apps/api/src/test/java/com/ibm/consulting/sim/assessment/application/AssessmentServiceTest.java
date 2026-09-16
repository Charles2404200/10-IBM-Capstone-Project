package com.ibm.consulting.sim.assessment.application;

import com.ibm.consulting.sim.achievement.application.AchievementEvaluationService;
import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock EngagementRepository engagementRepository;
    @Mock ResearchEvidenceRepository evidenceRepository;
    @Mock OutreachRepository outreachRepository;
    @Mock PersonaStateRepository personaStateRepository;
    @Mock ProposalRepository proposalRepository;
    @Mock ScenarioRepository scenarioRepository;
    @Mock AchievementEvaluationService achievementEvaluationService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final InMemoryAssessmentRepository assessmentRepository = new InMemoryAssessmentRepository();
    private AssessmentService service;

    @BeforeEach
    void setUp() {
        service = new AssessmentService(assessmentRepository, engagementRepository, evidenceRepository,
                outreachRepository, personaStateRepository, proposalRepository, scenarioRepository,
                achievementEvaluationService, eventPublisher);
    }

    @Test
    void generateFromClientDecisionCreatesScoresAndCompletesLifecycleOnce() {
        TestData data = engagementIn(EngagementState.CLIENT_DECISION);
        stubOwnedForUpdate(data);
        stubScoringInputs(data);

        AssessmentResponse response = service.generate(data.engagement().getId(), data.userId());

        assertThat(assessmentRepository.saveCount).isEqualTo(1);
        assertThat(response.competencyScores()).hasSize(4);
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.COMPLETED);
        assertThat(data.engagement().getCompletedAt()).isNotNull();
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.REVIEW
                        || event.getState() == EngagementState.COMPLETED)
                .extracting(event -> event.getState())
                .containsExactly(EngagementState.REVIEW, EngagementState.COMPLETED);
        verify(achievementEvaluationService).evaluateForUser(data.userId());
        verify(eventPublisher).publishEvent(any(AssessmentGeneratedEvent.class));
    }

    @Test
    void repeatedGenerateReturnsStoredAssessmentWithoutRepeatingBusinessSideEffects() {
        TestData data = engagementIn(EngagementState.CLIENT_DECISION);
        stubOwnedForUpdate(data);
        stubScoringInputs(data);

        AssessmentResponse first = service.generate(data.engagement().getId(), data.userId());
        AssessmentResponse replay = service.generate(data.engagement().getId(), data.userId());

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(assessmentRepository.saveCount).isEqualTo(1);
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.COMPLETED);
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.REVIEW)
                .hasSize(1);
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.COMPLETED)
                .hasSize(1);
        verify(achievementEvaluationService, times(1)).evaluateForUser(data.userId());
        verify(eventPublisher, times(1)).publishEvent(any(AssessmentGeneratedEvent.class));
    }

    @Test
    void generateBeforeClientDecisionIsRejectedWithoutPersistenceOrLifecycleChange() {
        TestData data = engagementIn(EngagementState.PREPARING);
        stubOwnedForUpdate(data);

        assertThatThrownBy(() -> service.generate(data.engagement().getId(), data.userId()))
                .isInstanceOf(AssessmentService.AssessmentNotAvailableException.class);

        assertThat(assessmentRepository.assessment).isEmpty();
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.PREPARING);
        verify(achievementEvaluationService, never()).evaluateForUser(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(engagementRepository, never()).save(any());
    }

    @Test
    void anotherUserCannotGenerateOrReadTheAssessment() {
        TestData data = engagementIn(EngagementState.CLIENT_DECISION);
        UUID otherUserId = UUID.randomUUID();
        when(engagementRepository.findByIdAndUserIdForUpdate(data.engagement().getId(), otherUserId))
                .thenReturn(Optional.empty());
        when(engagementRepository.findByIdAndUserId(data.engagement().getId(), otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(data.engagement().getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(data.engagement().getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThat(assessmentRepository.assessment).isEmpty();
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.CLIENT_DECISION);
        verify(achievementEvaluationService, never()).evaluateForUser(any());
    }

    private void stubOwnedForUpdate(TestData data) {
        when(engagementRepository.findByIdAndUserIdForUpdate(data.engagement().getId(), data.userId()))
                .thenReturn(Optional.of(data.engagement()));
    }

    private void stubScoringInputs(TestData data) {
        when(evidenceRepository.findByEngagementId(data.engagement().getId())).thenReturn(List.of());
        when(outreachRepository.findByEngagementId(data.engagement().getId())).thenReturn(List.of());
        when(personaStateRepository.findByEngagementId(data.engagement().getId())).thenReturn(Optional.empty());
        when(proposalRepository.findByEngagementId(data.engagement().getId())).thenReturn(Optional.empty());
        when(scenarioRepository.findById(data.scenario().getId())).thenReturn(Optional.of(data.scenario()));
    }

    private TestData engagementIn(EngagementState targetState) {
        UUID userId = UUID.randomUUID();
        Scenario scenario = Scenario.create("Assessment", "Technology", "Scenario", 3);
        Engagement engagement = Engagement.start(userId, scenario.getId(), UUID.randomUUID());
        engagement.selectLead(UUID.randomUUID());
        if (targetState == EngagementState.PREPARING) {
            engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
            engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
            engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
            engagement.transitionTo(EngagementState.PREPARING, "Preparation complete");
            return new TestData(userId, engagement, scenario);
        }
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "Preparation complete");
        engagement.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        engagement.transitionTo(EngagementState.DISCOVERY_COMPLETE, "Discovery complete");
        engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "Proposal drafted");
        engagement.transitionTo(EngagementState.PROPOSAL_SUBMITTED, "Proposal submitted");
        engagement.transitionTo(EngagementState.CLIENT_DECISION, "Client decided");
        return new TestData(userId, engagement, scenario);
    }

    private static final class InMemoryAssessmentRepository implements AssessmentRepository {
        private Optional<Assessment> assessment = Optional.empty();
        private int saveCount;

        @Override public Assessment save(Assessment assessment) {
            this.assessment = Optional.of(assessment);
            saveCount++;
            return assessment;
        }

        @Override public Optional<Assessment> findByEngagementId(UUID engagementId) {
            return assessment.filter(candidate -> engagementId.equals(candidate.getEngagementId()));
        }

        @Override public List<Assessment> findAllByEngagementIdIn(List<UUID> engagementIds) {
            return assessment.filter(candidate -> engagementIds.contains(candidate.getEngagementId())).stream().toList();
        }
    }

    private record TestData(UUID userId, Engagement engagement, Scenario scenario) {}
}
