package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProposalLifecycleGuardTest {

    @ParameterizedTest
    @EnumSource(value = EngagementState.class, names = {"DISCOVERY_COMPLETE", "PROPOSAL_DRAFT"})
    void reviewAndChallengeAreAvailableOnlyInProposalAuthoringStates(EngagementState state) {
        Fixture fixture = fixture(state);

        assertThatNoException().isThrownBy(() -> fixture.service().review(
                fixture.engagementId(), fixture.userId(), draft()));
        assertThatNoException().isThrownBy(() -> fixture.service().challenge(
                fixture.engagementId(), fixture.userId(), draft()));

        verify(fixture.engagements(), never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EngagementState.class, names = {"QUALIFYING", "CLIENT_DECISION", "REVIEW", "COMPLETED"})
    void lifecycleRejectionsOccurBeforeAnyAiCall(EngagementState state) {
        Fixture fixture = fixture(state);

        assertThatThrownBy(() -> fixture.service().review(fixture.engagementId(), fixture.userId(), draft()))
                .isInstanceOf(ProposalService.InvalidProposalStateException.class);
        assertThatThrownBy(() -> fixture.service().challenge(fixture.engagementId(), fixture.userId(), draft()))
                .isInstanceOf(ProposalService.InvalidProposalStateException.class);

        verifyNoInteractions(fixture.ai());
    }

    private Fixture fixture(EngagementState state) {
        UUID engagementId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Engagement engagement = mock(Engagement.class);
        when(engagement.getId()).thenReturn(engagementId);
        when(engagement.getState()).thenReturn(state);
        EngagementRepository engagements = mock(EngagementRepository.class);
        when(engagements.findByIdAndUserId(engagementId, userId)).thenReturn(Optional.of(engagement));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.execute(anyString(), any(), anyString(), anyInt(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(5)).get());
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(engagement)).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));

        ProposalService service = new ProposalService(mock(ProposalRepository.class), engagements,
                mock(ResearchEvidenceRepository.class), mock(PersonaStateRepository.class), mock(MeetingRepository.class),
                mock(ConversationTurnRepository.class), ai, new ObjectMapper(), mock(PersonaCatalogService.class),
                difficulty, new ConcurrentMapCacheManager(CacheConfig.PROPOSAL_REVIEW_CACHE),
                mock(ApplicationEventPublisher.class));
        return new Fixture(service, engagements, ai, engagementId, userId);
    }

    private ProposalDraftContent draft() {
        return new ProposalDraftContent("Grounded problem", "Focused pilot", List.of("Read-only integration"),
                BigDecimal.valueOf(100_000), 8, "UNCONFIRMED", "Estimate", List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private record Fixture(ProposalService service, EngagementRepository engagements,
                           AiOrchestrationService ai, UUID engagementId, UUID userId) {}
}
