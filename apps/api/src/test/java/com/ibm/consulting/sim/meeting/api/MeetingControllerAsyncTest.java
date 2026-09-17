package com.ibm.consulting.sim.meeting.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.meeting.application.GuidedMeetingResponseService;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationService;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import com.ibm.consulting.sim.shared.config.AsyncConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeetingControllerAsyncTest {

    @Test
    void sseWorkUsesABoundedExecutor() {
        ExecutorService executor = new AsyncConfig(1, 2, 4).aiGatewayExecutor();
        MeetingController controller = controller(mock(MeetingService.class), executor);

        assertThat(ReflectionTestUtils.getField(controller, "sseExecutor")).isSameAs(executor);
        assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isLessThan(Integer.MAX_VALUE);

        executor.shutdownNow();
    }

    @Test
    void timingOutTheEmitterInterruptsTheMeetingWorker() throws Exception {
        MeetingService meetingService = mock(MeetingService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(meetingService.sendMessage(any(), any(), any(), any())).thenAnswer(ignored -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("blocked operation unexpectedly resumed");
            } catch (InterruptedException expected) {
                interrupted.countDown();
                throw expected;
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        MeetingController controller = controller(meetingService, executor);

        try {
            User user = User.create("learner@example.com", "hash", "Learner", UserRole.LEARNER);
            SseEmitter emitter = controller.sendMessage(UUID.randomUUID(),
                    new MeetingController.MessageRequest("Hello", "message-1"), user);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            ((Runnable) ReflectionTestUtils.getField(emitter, "timeoutCallback")).run();

            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private MeetingController controller(MeetingService meetingService, ExecutorService executor) {
        return new MeetingController(
                mock(MeetingPreparationService.class),
                meetingService,
                mock(GuidedMeetingResponseService.class),
                executor);
    }
}
