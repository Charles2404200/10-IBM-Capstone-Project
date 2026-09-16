package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingCompletionOutcome;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class MeetingResponseTest {

    @Test
    void detachesDebriefTipsFromPersistenceBackedCollection() {
        Meeting meeting = org.mockito.Mockito.mock(Meeting.class);
        List<String> persistenceBackedTips = new ArrayList<>(List.of("Confirm the decision process."));
        when(meeting.getId()).thenReturn(UUID.randomUUID());
        when(meeting.getEngagementId()).thenReturn(UUID.randomUUID());
        when(meeting.getPersonaId()).thenReturn(UUID.randomUUID());
        when(meeting.getStatus()).thenReturn(MeetingStatus.COMPLETED);
        when(meeting.getCompletionOutcome()).thenReturn(MeetingCompletionOutcome.PASSED);
        when(meeting.getDebriefTips()).thenReturn(persistenceBackedTips);

        MeetingResponse response = MeetingResponse.from(meeting);
        persistenceBackedTips.add("This must not leak into the API response.");

        assertEquals(List.of("Confirm the decision process."), response.debriefTips());
        assertThrows(UnsupportedOperationException.class,
                () -> response.debriefTips().add("Responses are immutable."));
    }

    @Test
    void exposesFreeformModeAndHigherGateForHardMeetings() {
        Meeting meeting = org.mockito.Mockito.mock(Meeting.class);
        when(meeting.getId()).thenReturn(UUID.randomUUID());
        when(meeting.getEngagementId()).thenReturn(UUID.randomUUID());
        when(meeting.getPersonaId()).thenReturn(UUID.randomUUID());
        when(meeting.getStatus()).thenReturn(MeetingStatus.IN_PROGRESS);
        when(meeting.getDebriefTips()).thenReturn(List.of());
        when(meeting.getBehaviourLedger()).thenReturn(List.of());

        MeetingResponse response = MeetingResponse.from(meeting, DifficultyProfile.defaults(5, 5, 5, 5), false, 0);

        assertEquals("FREEFORM", response.interactionMode());
        assertEquals(80, response.meetingThreshold());
    }

    @Test
    void exposesGuidedChoicesForEasyAndMediumMeetings() {
        Meeting meeting = org.mockito.Mockito.mock(Meeting.class);
        when(meeting.getId()).thenReturn(UUID.randomUUID());
        when(meeting.getEngagementId()).thenReturn(UUID.randomUUID());
        when(meeting.getPersonaId()).thenReturn(UUID.randomUUID());
        when(meeting.getStatus()).thenReturn(MeetingStatus.IN_PROGRESS);
        when(meeting.getDebriefTips()).thenReturn(List.of());
        when(meeting.getBehaviourLedger()).thenReturn(List.of());

        assertEquals("GUIDED", MeetingResponse.from(meeting, DifficultyProfile.defaults(1, 1, 1, 1), false, 0).interactionMode());
        assertEquals("GUIDED", MeetingResponse.from(meeting, DifficultyProfile.defaults(3, 3, 3, 3), false, 0).interactionMode());
    }
}
