package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidMeetingStateException extends DomainException {
    public InvalidMeetingStateException(String message) {
        super(message);
    }
}
