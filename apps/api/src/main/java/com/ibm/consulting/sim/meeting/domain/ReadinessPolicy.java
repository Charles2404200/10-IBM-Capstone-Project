package com.ibm.consulting.sim.meeting.domain;

import java.util.List;

/**
 * Deterministic readiness scoring for meeting preparation (§4.3 US-05).
 * Pure domain logic — no Spring/JPA dependency.
 */
public final class ReadinessPolicy {

    public static final int READY_THRESHOLD = 70;

    private static final int MAX_AGENDA_CREDIT = 40;
    private static final int MAX_QUESTION_CREDIT = 40;
    private static final int OBJECTIVE_CREDIT = 20;
    private static final int CREDIT_PER_AGENDA_ITEM = 10;
    private static final int CREDIT_PER_QUESTION = 8;

    private ReadinessPolicy() {}

    public static int calculate(String objective, List<String> agenda, List<String> discoveryQuestions) {
        int objectiveScore = (objective != null && !objective.isBlank()) ? OBJECTIVE_CREDIT : 0;
        int agendaScore = Math.min(MAX_AGENDA_CREDIT, countMeaningful(agenda) * CREDIT_PER_AGENDA_ITEM);
        int questionScore = Math.min(MAX_QUESTION_CREDIT, countMeaningful(discoveryQuestions) * CREDIT_PER_QUESTION);
        return Math.min(100, objectiveScore + agendaScore + questionScore);
    }

    private static int countMeaningful(List<String> items) {
        if (items == null) {
            return 0;
        }
        return (int) items.stream().filter(i -> i != null && !i.isBlank()).count();
    }
}
