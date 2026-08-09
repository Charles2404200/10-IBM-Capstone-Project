package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingCompletionOutcome;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
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
}
