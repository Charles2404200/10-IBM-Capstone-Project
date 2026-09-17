package com.ibm.consulting.sim.meeting.infrastructure.realtime;

import com.ibm.consulting.sim.meeting.application.MeetingRequestLimits;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingSocketControllerValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void stompPayloadAppliesTheSameMessageAndIdLimitsAsHttp() {
        var valid = new MeetingSocketController.MeetingMessage(
                "m".repeat(MeetingRequestLimits.MESSAGE_MAX_LENGTH),
                "i".repeat(MeetingRequestLimits.MESSAGE_ID_MAX_LENGTH));
        var oversizedMessage = new MeetingSocketController.MeetingMessage(
                "m".repeat(MeetingRequestLimits.MESSAGE_MAX_LENGTH + 1), "message-1");
        var oversizedId = new MeetingSocketController.MeetingMessage(
                "valid", "i".repeat(MeetingRequestLimits.MESSAGE_ID_MAX_LENGTH + 1));

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(oversizedMessage))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("message");
        assertThat(validator.validate(oversizedId))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("messageId");
    }
}
