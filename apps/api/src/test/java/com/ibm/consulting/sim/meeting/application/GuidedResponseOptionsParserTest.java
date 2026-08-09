package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidedResponseOptionsParserTest {

    private final GuidedResponseOptionsParser parser = new GuidedResponseOptionsParser(new ObjectMapper());

    @Test
    void acceptsExactlyThreeDistinctResponseOptions() throws Exception {
        GuidedResponseOptions result = parser.parse("""
                {"options":[
                  "I would validate the reconciliation delay first and agree a measurable pilot outcome with your team.",
                  "Before proposing a wider change, could we map the current handoff constraints with the clinical leads?",
                  "I can outline a phased approach, but I would first confirm which operational risk needs attention this quarter."
                ]}
                """);

        assertThat(result.options()).hasSize(3);
    }

    @Test
    void rejectsAnythingOtherThanThreeDistinctOptions() {
        assertThatThrownBy(() -> parser.parse("""
                {"options":["A sufficiently detailed professional response for the client.",
                "A sufficiently detailed professional response for the client."]}
                """))
                .isInstanceOf(AiValidationException.class);
    }
}
