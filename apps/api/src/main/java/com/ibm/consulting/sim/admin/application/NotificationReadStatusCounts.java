package com.ibm.consulting.sim.admin.application;

/** Database-computed audience counts for an administrator's read-status view. */
public record NotificationReadStatusCounts(long recipientCount, long readCount) {

    public long unreadCount() {
        return Math.max(0, recipientCount - readCount);
    }
}
