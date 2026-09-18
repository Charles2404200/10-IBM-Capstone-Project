package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationObject;

public record NotificationPersistedEvent(
        NotificationObject notification
) {
}