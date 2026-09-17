package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ProposalBusinessOutcome;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import com.ibm.consulting.sim.proposal.domain.ProposalEvidenceLink;
import com.ibm.consulting.sim.proposal.domain.ProposalMilestone;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.proposal.domain.ProposalRisk;
import com.ibm.consulting.sim.proposal.domain.ProposalStatus;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalSubmissionServiceTest {

    @Mock EngagementRepository engagementRepository;
    @Mock ResearchEvidenceRepository evidenceRepository;
    @Mock PersonaStateRepository personaStateRepository;
    @Mock MeetingRepository meetingRepository;
    @Mock ConversationTurnRepository turnRepository;
    @Mock AiOrchestrationService aiOrchestrationService;
    @Mock PersonaCatalogService personaCatalogService;
    @Mock DifficultyProfileService difficultyProfileService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final InMemoryProposalRepository proposalRepository = new InMemoryProposalRepository();
    private ProposalService service;
    private TestData data;

    @BeforeEach
    void setUp() {
        data = discoveryCompleteEngagement();
        service = new ProposalService(proposalRepository, engagementRepository, evidenceRepository,
                personaStateRepository, meetingRepository, turnRepository, aiOrchestrationService,
                new ObjectMapper(), personaCatalogService, difficultyProfileService,
                new ConcurrentMapCacheManager(CacheConfig.PROPOSAL_REVIEW_CACHE,
                        CacheConfig.PROPOSAL_DECISION_NARRATIVE_CACHE), eventPublisher);
        when(engagementRepository.findByIdAndUserIdForUpdate(data.engagement().getId(), data.userId()))
                .thenReturn(Optional.of(data.engagement()));
        when(evidenceRepository.findByEngagementId(data.engagement().getId())).thenReturn(List.of(data.evidence()));
        when(meetingRepository.findByEngagementId(data.engagement().getId())).thenReturn(Optional.empty());
        when(difficultyProfileService.forEngagement(data.engagement())).thenReturn(data.profile());
    }

    @Test
    void blockingValidationRejectsAtomicallyBeforeProposalDecisionOrLifecycleMutation() {
        ProposalDraftContent invalid = validDraft(List.of());

        assertThatThrownBy(() -> service.submit(data.engagement().getId(), data.userId(), invalid, true))
                .isInstanceOf(ProposalService.ProposalValidationException.class)
                .satisfies(exception -> assertThat(((ProposalService.ProposalValidationException) exception).getIssues())
                        .anyMatch(issue -> "BLOCKING".equals(issue.severity())));

        assertThat(proposalRepository.proposal).isEmpty();
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.DISCOVERY_COMPLETE);
        verify(engagementRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(personaCatalogService, never()).getPersona(any());
        verify(aiOrchestrationService, never()).execute(anyString(), any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void validSubmissionPersistsDeterministicDecisionAndLifecycleExactlyOnce() {
        stubDecisionInputs();
        ProposalDraftContent valid = validDraft(
                List.of(new ProposalEvidenceLink("PROBLEM", "evidence:" + data.evidence().getId())));

        ProposalResponse response = service.submit(data.engagement().getId(), data.userId(), valid, true);

        Proposal proposal = proposalRepository.proposal.orElseThrow();
        assertThat(response.status()).isEqualTo(ProposalStatus.SUBMITTED.name());
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SUBMITTED);
        assertThat(proposal.getClientDecisionOutcome()).isNotNull();
        assertThat(proposal.getDecisionRationale()).isNotBlank();
        assertThat(proposal.getDecisionDimensions()).isNotEmpty();
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.CLIENT_DECISION);
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.PROPOSAL_DRAFT
                        || event.getState() == EngagementState.PROPOSAL_SUBMITTED
                        || event.getState() == EngagementState.CLIENT_DECISION)
                .extracting(event -> event.getState())
                .containsExactly(EngagementState.PROPOSAL_DRAFT, EngagementState.PROPOSAL_SUBMITTED,
                        EngagementState.CLIENT_DECISION);
        verify(eventPublisher).publishEvent(any(ProposalDecisionSubmittedEvent.class));
        verify(aiOrchestrationService, never()).execute(anyString(), any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void repeatedSubmissionCannotCreateASecondDecisionOrLifecycleSequence() {
        stubDecisionInputs();
        ProposalDraftContent valid = validDraft(
                List.of(new ProposalEvidenceLink("PROBLEM", "evidence:" + data.evidence().getId())));
        service.submit(data.engagement().getId(), data.userId(), valid, true);

        assertThatThrownBy(() -> service.submit(data.engagement().getId(), data.userId(), valid, true))
                .isInstanceOf(ProposalService.InvalidProposalStateException.class);

        assertThat(proposalRepository.saveCount).isEqualTo(1);
        verify(eventPublisher, times(1)).publishEvent(any(ProposalDecisionSubmittedEvent.class));
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.CLIENT_DECISION)
                .hasSize(1);
    }

    @Test
    void narrativeFailureCannotChangeTheAlreadyAuthoritativeSubmissionOutcome() {
        stubDecisionInputs();
        ProposalDraftContent valid = validDraft(
                List.of(new ProposalEvidenceLink("PROBLEM", "evidence:" + data.evidence().getId())));
        ArgumentCaptor<ProposalDecisionSubmittedEvent> event =
                ArgumentCaptor.forClass(ProposalDecisionSubmittedEvent.class);
        service.submit(data.engagement().getId(), data.userId(), valid, true);
        verify(eventPublisher).publishEvent(event.capture());
        Proposal proposal = proposalRepository.proposal.orElseThrow();
        var outcome = proposal.getClientDecisionOutcome();
        String rationale = proposal.getDecisionRationale();
        when(aiOrchestrationService.execute(anyString(), any(), anyString(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("narrative provider unavailable"));
        ProposalDecisionNarrativeEnricher enricher = new ProposalDecisionNarrativeEnricher(
                proposalRepository, aiOrchestrationService, new ObjectMapper(),
                new ConcurrentMapCacheManager(CacheConfig.PROPOSAL_DECISION_NARRATIVE_CACHE));

        assertThatThrownBy(() -> enricher.enrich(event.getValue()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("narrative provider unavailable");

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SUBMITTED);
        assertThat(proposal.getClientDecisionOutcome()).isEqualTo(outcome);
        assertThat(proposal.getDecisionRationale()).isEqualTo(rationale);
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.CLIENT_DECISION);
    }

    private TestData discoveryCompleteEngagement() {
        UUID userId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        engagement.selectLead(leadId);
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "Preparation complete");
        engagement.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        engagement.transitionTo(EngagementState.DISCOVERY_COMPLETE, "Discovery complete");
        ResearchEvidence evidence = ResearchEvidence.builder()
                .engagementId(engagement.getId())
                .leadId(leadId)
                .note("Manual reconciliation is creating audit delays and operational risk.")
                .evidenceType(EvidenceType.COMPANY_NEWS)
                .sourceTitle("Operational review")
                .confidence(ConfidenceLevel.HIGH)
                .sequenceNo(1)
                .build();
        PersonaProfile persona = new PersonaProfile();
        persona.setId(engagement.getPersonaId());
        persona.setName("Client");
        persona.setJobTitle("CIO");
        persona.setOrganisation("Example Co");
        persona.setCommunicationStyle("Direct");
        return new TestData(userId, engagement, evidence, persona,
                DifficultyProfile.defaults(3, 3, 3, 3));
    }

    private void stubDecisionInputs() {
        when(personaStateRepository.findByEngagementId(data.engagement().getId())).thenReturn(Optional.empty());
        when(personaCatalogService.getPersona(data.engagement().getPersonaId())).thenReturn(data.persona());
    }

    private ProposalDraftContent validDraft(List<ProposalEvidenceLink> evidenceLinks) {
        return new ProposalDraftContent(
                "Manual reconciliation is causing audit delays and operational risk across the network.",
                "Run a controlled integration pilot with early validation and a rollback plan.",
                List.of("Integration pilot"), BigDecimal.valueOf(150_000), 8,
                "MEDIUM", "Consultant estimate",
                List.of(new ProposalBusinessOutcome("Reduce reconciliation effort", "Hours per case", "30% reduction")),
                List.of(new ProposalMilestone("Workflow mapping", "Week 1-2")),
                List.of(new ProposalRisk("Legacy integration", "HIGH", "Validate adapters before pilot")),
                List.of("Client SMEs are available for validation"), evidenceLinks);
    }

    private static final class InMemoryProposalRepository implements ProposalRepository {
        private Optional<Proposal> proposal = Optional.empty();
        private int saveCount;

        @Override public Proposal save(Proposal proposal) {
            this.proposal = Optional.of(proposal);
            saveCount++;
            return proposal;
        }

        @Override public Optional<Proposal> findByEngagementId(UUID engagementId) {
            return proposal.filter(candidate -> engagementId.equals(candidate.getEngagementId()));
        }
    }

    private record TestData(UUID userId, Engagement engagement, ResearchEvidence evidence,
                            PersonaProfile persona, DifficultyProfile profile) {}
}
