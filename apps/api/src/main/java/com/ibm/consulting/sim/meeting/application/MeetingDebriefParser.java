package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;

import java.util.ArrayList;
import java.util.List;

public class MeetingDebriefParser implements AiResponseParser<MeetingDebriefNarrative> {
    private final ObjectMapper mapper;

    public MeetingDebriefParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MeetingDebriefNarrative parse(String rawJson) throws AiValidationException {
        try {
            JsonNode root = mapper.readTree(rawJson);
            String feedback = root.path("feedback").asText("").trim();
            if (feedback.isBlank()) throw new AiValidationException("Missing debrief feedback");
            List<String> tips = new ArrayList<>();
            root.path("tips").forEach(tip -> {
                String value = tip.asText("").trim();
                if (!value.isBlank()) tips.add(value);
            });
            if (tips.isEmpty()) throw new AiValidationException("Missing debrief tips");
            return new MeetingDebriefNarrative(feedback, List.copyOf(tips));
        } catch (AiValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiValidationException("Response is not valid meeting debrief JSON", exception);
        }
    }
}
