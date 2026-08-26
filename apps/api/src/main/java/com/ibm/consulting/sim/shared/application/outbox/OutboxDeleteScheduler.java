package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxDeleteScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxDeleteScheduler.class);

    private static final Duration PUBLISHED_EVENT_RETENTION =
            Duration.ofDays(2);

    private final OutboxEventRepository repository;

    public OutboxDeleteScheduler(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Removes successfully published outbox rows after the retention window.
     * Pending and processing events are never selected by the repository cleanup
     * query and therefore cannot be deleted by this scheduler.
     */
    @Scheduled(
            cron = "${app.kafka.outbox.cleanup-cron:0 */10 * * * *}"
    )
    @Transactional
    public void deletePublishedEvents() {
        Instant cutoff = Instant.now().minus(PUBLISHED_EVENT_RETENTION);

        repository.deletePublishedBefore(cutoff);

        log.debug(
                "Completed published outbox cleanup: cutoff={}",
                cutoff
        );
    }
}
