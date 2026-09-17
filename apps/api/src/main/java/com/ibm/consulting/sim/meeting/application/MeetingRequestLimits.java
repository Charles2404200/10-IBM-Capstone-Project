package com.ibm.consulting.sim.meeting.application;

/** Shared HTTP/STOMP limits for learner-authored, AI-facing meeting content. */
public final class MeetingRequestLimits {

    public static final int OBJECTIVE_MAX_LENGTH = 300;
    public static final int PLAN_MAX_ITEMS = 10;
    public static final int PLAN_ITEM_MAX_LENGTH = 300;
    public static final int MESSAGE_MAX_LENGTH = 4_000;
    public static final int MESSAGE_ID_MAX_LENGTH = 64;

    private MeetingRequestLimits() {}
}
