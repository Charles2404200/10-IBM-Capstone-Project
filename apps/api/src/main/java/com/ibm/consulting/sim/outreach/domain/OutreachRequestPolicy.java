package com.ibm.consulting.sim.outreach.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic translation of a client reply into the next learner action.
 * The LLM may phrase the reply, but it does not choose the product workflow.
 */
public final class OutreachRequestPolicy {

    private OutreachRequestPolicy() {}

    public static OutreachNextAction nextActionFor(OutreachOutcome outcome, String clientReply) {
        if (outcome == OutreachOutcome.ACCEPTED) {
            return OutreachNextAction.CONTINUE_TO_MEETING;
        }
        if (outcome == OutreachOutcome.REJECTED) {
            return OutreachNextAction.NONE;
        }
        return requestsCapabilityBrief(clientReply)
                ? OutreachNextAction.SUBMIT_CAPABILITY_BRIEF
                : OutreachNextAction.SEND_FOLLOW_UP;
    }

    public static OutreachRequestDetails detailsFor(OutreachOutcome outcome, String clientReply,
                                                     OutreachNextAction persistedAction) {
        OutreachNextAction action = persistedAction != null
                ? persistedAction
                : nextActionFor(outcome, clientReply);
        if (action != OutreachNextAction.SUBMIT_CAPABILITY_BRIEF) {
            return new OutreachRequestDetails(action, null, null, List.of());
        }

        String reply = clientReply == null ? "" : clientReply.toLowerCase(Locale.ROOT);
        List<String> requirements = new ArrayList<>();
        if (reply.contains("pharmaceutical")) requirements.add("Relevant pharmaceutical distribution experience");
        if (reply.contains("phased")) requirements.add("Phased implementation approach");
        if (reply.contains("wms") || reply.contains("warehouse")) requirements.add("Integration with existing WMS");
        if (reply.contains("risk") || reply.contains("disruption")) requirements.add("Risk and disruption controls");
        if (reply.contains("case stud") || reply.contains("case example")) requirements.add("Relevant case examples");
        if (requirements.isEmpty()) requirements.add("Address the capability request in the latest client response");

        return new OutreachRequestDetails(
                action,
                "One-page capability brief",
                "The client asked for a concise capability summary before deciding whether to reconnect.",
                List.copyOf(requirements));
    }

    private static boolean requestsCapabilityBrief(String clientReply) {
        if (clientReply == null || clientReply.isBlank()) return false;
        String reply = clientReply.toLowerCase(Locale.ROOT);
        boolean document = reply.contains("one-page") || reply.contains("one page")
                || reply.contains("capability brief") || reply.contains("capability summary")
                || (reply.contains("summary") && (reply.contains("experience") || reply.contains("send it over")));
        boolean request = reply.contains("send") || reply.contains("share") || reply.contains("provide")
                || reply.contains("would be helpful") || reply.contains("review");
        return document && request;
    }
}
