package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparation;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.InvalidMeetingStateException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages the pre-meeting planning workspace. Readiness is always recomputed
 * server-side (§4.3 US-05) — the frontend never sets it directly.
 */
@Service
public class MeetingPreparationService {

    private final MeetingPreparationRepository preparationRepository;
    private final EngagementRepository engagementRepository;

    public MeetingPreparationService(MeetingPreparationRepository preparationRepository,
                                      EngagementRepository engagementRepository) {
        this.preparationRepository = preparationRepository;
        this.engagementRepository = engagementRepository;
    }

    @Transactional
    public MeetingPreparationResponse update(UUID engagementId, UUID userId, String objective,
                                              List<String> agenda, List<String> discoveryQuestions) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        if (engagement.getState() != EngagementState.MEETING_SECURED
                && engagement.getState() != EngagementState.PREPARING) {
            throw new InvalidMeetingStateException(
                    "Cannot edit preparation in state: " + engagement.getState());
        }

        MeetingPreparation preparation = preparationRepository.findByEngagementId(engagementId)
                .orElseGet(() -> MeetingPreparation.start(engagementId));
        preparation.update(objective, agenda, discoveryQuestions);
        preparationRepository.save(preparation);

        if (preparation.isReady() && engagement.getState() == EngagementState.MEETING_SECURED) {
            engagement.transitionTo(EngagementState.PREPARING,
                    "Preparation readiness reached %d".formatted(preparation.getReadinessScore()));
            engagementRepository.save(engagement);
        }

        return MeetingPreparationResponse.from(preparation);
    }

    @Transactional(readOnly = true)
    public MeetingPreparationResponse get(UUID engagementId, UUID userId) {
        engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        MeetingPreparation preparation = preparationRepository.findByEngagementId(engagementId)
                .orElseGet(() -> MeetingPreparation.start(engagementId));
        return MeetingPreparationResponse.from(preparation);
    }
}
