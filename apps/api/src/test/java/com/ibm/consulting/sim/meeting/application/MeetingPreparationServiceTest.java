package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.meeting.domain.InvalidMeetingStateException;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparation;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.ReadinessPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingPreparationServiceTest {

    @Test
    void incompletePreparationPersistsWithoutAdvancingLifecycle() {
        UUID userId = UUID.randomUUID();
        Engagement engagement = meetingSecuredEngagement(userId);
        EngagementRepository engagements = ownedEngagements(engagement, userId);
        InMemoryPreparationRepository preparations = new InMemoryPreparationRepository();
        MeetingPreparationService service = new MeetingPreparationService(preparations, engagements);
        String objective = "Understand the client's priorities";

        MeetingPreparationResponse response = service.update(
                engagement.getId(), userId, objective, List.of(), List.of());

        MeetingPreparation persisted = preparations.findByEngagementId(engagement.getId()).orElseThrow();
        assertThat(persisted.getObjective()).isEqualTo(objective);
        assertThat(persisted.getReadinessScore()).isLessThan(ReadinessPolicy.READY_THRESHOLD);
        assertThat(response.readinessScore()).isEqualTo(persisted.getReadinessScore());
        assertThat(response.ready()).isFalse();
        assertThat(engagement.getState()).isEqualTo(EngagementState.MEETING_SECURED);
        assertThat(engagement.getEvents())
                .noneMatch(event -> event.getState() == EngagementState.PREPARING);
        verify(engagements, never()).save(engagement);
    }

    @Test
    void incompatibleLifecycleStateRejectsWithoutPreparationMutation() {
        UUID userId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        EngagementRepository engagements = ownedEngagements(engagement, userId);
        InMemoryPreparationRepository preparations = new InMemoryPreparationRepository();
        MeetingPreparationService service = new MeetingPreparationService(preparations, engagements);

        assertThatThrownBy(() -> service.update(engagement.getId(), userId,
                "Objective", List.of(), List.of()))
                .isInstanceOf(InvalidMeetingStateException.class);

        assertThat(preparations.findByEngagementId(engagement.getId())).isEmpty();
        assertThat(engagement.getState()).isEqualTo(EngagementState.QUALIFYING);
        verify(engagements, never()).save(engagement);
    }

    private Engagement meetingSecuredEngagement(UUID userId) {
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        engagement.selectLead(UUID.randomUUID());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Research ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        return engagement;
    }

    private EngagementRepository ownedEngagements(Engagement engagement, UUID userId) {
        EngagementRepository engagements = mock(EngagementRepository.class);
        when(engagements.findByIdAndUserIdForUpdate(engagement.getId(), userId))
                .thenReturn(Optional.of(engagement));
        return engagements;
    }

    private static final class InMemoryPreparationRepository implements MeetingPreparationRepository {
        private final List<MeetingPreparation> preparations = new ArrayList<>();

        @Override public MeetingPreparation save(MeetingPreparation preparation) {
            preparations.removeIf(existing -> existing.getEngagementId().equals(preparation.getEngagementId()));
            preparations.add(preparation);
            return preparation;
        }

        @Override public Optional<MeetingPreparation> findByEngagementId(UUID engagementId) {
            return preparations.stream()
                    .filter(preparation -> preparation.getEngagementId().equals(engagementId))
                    .findFirst();
        }
    }
}
