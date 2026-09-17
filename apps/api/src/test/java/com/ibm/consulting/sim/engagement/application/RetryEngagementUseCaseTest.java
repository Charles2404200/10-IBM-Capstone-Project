package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryEngagementUseCaseTest {

    private static final String FROZEN_DIFFICULTY = "{\"version\":1,\"tier\":\"HARD\"}";

    @Test
    void validRetryPreservesLineageLeadPersonaScenarioAndFrozenDifficulty() {
        Engagement failed = failedEngagement(UUID.randomUUID());
        InMemoryEngagementRepository repository = new InMemoryEngagementRepository(failed);
        RetryEngagementUseCase useCase = new RetryEngagementUseCase(repository);

        EngagementResponse response = useCase.execute(failed.getId(), failed.getUserId());

        Engagement retry = repository.retriesOf(failed.getId()).getFirst();
        assertThat(response.id()).isEqualTo(retry.getId());
        assertThat(retry.getRetryOfEngagementId()).isEqualTo(failed.getId());
        assertThat(retry.getUserId()).isEqualTo(failed.getUserId());
        assertThat(retry.getScenarioId()).isEqualTo(failed.getScenarioId());
        assertThat(retry.getPersonaId()).isEqualTo(failed.getPersonaId());
        assertThat(retry.getSelectedLeadId()).isEqualTo(failed.getSelectedLeadId());
        assertThat(retry.getDifficultyProfileSnapshot()).isEqualTo(FROZEN_DIFFICULTY);
        assertThat(retry.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
        assertThat(failed.getState()).isEqualTo(EngagementState.MEETING_FAILED);
    }

    @Test
    void repeatedRetryReusesTheExistingActiveWorkspace() {
        Engagement failed = failedEngagement(UUID.randomUUID());
        InMemoryEngagementRepository repository = new InMemoryEngagementRepository(failed);
        RetryEngagementUseCase useCase = new RetryEngagementUseCase(repository);

        EngagementResponse first = useCase.execute(failed.getId(), failed.getUserId());
        EngagementResponse replay = useCase.execute(failed.getId(), failed.getUserId());

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(repository.retriesOf(failed.getId())).hasSize(1);
    }

    @Test
    void nonFailedEngagementCannotCreateARetry() {
        UUID userId = UUID.randomUUID();
        Engagement active = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID(), FROZEN_DIFFICULTY);
        active.selectLead(UUID.randomUUID());
        InMemoryEngagementRepository repository = new InMemoryEngagementRepository(active);

        assertThatThrownBy(() -> new RetryEngagementUseCase(repository).execute(active.getId(), userId))
                .isInstanceOf(RetryEngagementUseCase.RetryNotAvailableException.class);

        assertThat(repository.retriesOf(active.getId())).isEmpty();
        assertThat(active.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
    }

    @Test
    void anotherUserCannotDiscoverOrRetryTheFailedEngagement() {
        Engagement failed = failedEngagement(UUID.randomUUID());
        InMemoryEngagementRepository repository = new InMemoryEngagementRepository(failed);

        assertThatThrownBy(() -> new RetryEngagementUseCase(repository)
                .execute(failed.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);

        assertThat(repository.retriesOf(failed.getId())).isEmpty();
        assertThat(failed.getState()).isEqualTo(EngagementState.MEETING_FAILED);
    }

    private Engagement failedEngagement(UUID userId) {
        Engagement failed = Engagement.start(
                userId, UUID.randomUUID(), UUID.randomUUID(), FROZEN_DIFFICULTY);
        failed.selectLead(UUID.randomUUID());
        failed.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        failed.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        failed.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        failed.transitionTo(EngagementState.PREPARING, "Preparation complete");
        failed.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        failed.transitionTo(EngagementState.MEETING_FAILED, "Meeting failed");
        return failed;
    }

    private static final class InMemoryEngagementRepository implements EngagementRepository {
        private final List<Engagement> engagements = new ArrayList<>();

        private InMemoryEngagementRepository(Engagement... initial) {
            engagements.addAll(List.of(initial));
        }

        @Override public Engagement save(Engagement engagement) {
            if (engagements.stream().noneMatch(existing -> existing.getId().equals(engagement.getId()))) {
                engagements.add(engagement);
            }
            return engagement;
        }

        @Override public List<Engagement> findAll() { return List.copyOf(engagements); }

        @Override public Optional<Engagement> findById(UUID id) {
            return engagements.stream().filter(engagement -> id.equals(engagement.getId())).findFirst();
        }

        @Override public List<Engagement> findByUserId(UUID userId) {
            return engagements.stream().filter(engagement -> userId.equals(engagement.getUserId())).toList();
        }

        @Override public List<Engagement> findDashboardByUserId(UUID userId) {
            return findByUserId(userId);
        }

        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> userId.equals(engagement.getUserId()));
        }

        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            return findByIdAndUserId(id, userId);
        }

        private List<Engagement> retriesOf(UUID failedEngagementId) {
            return engagements.stream()
                    .filter(engagement -> failedEngagementId.equals(engagement.getRetryOfEngagementId()))
                    .toList();
        }
    }
}
