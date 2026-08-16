package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.BaseEmailService;
import com.ibm.consulting.sim.identity.domain.EmailRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailListenerService {

    private final BaseEmailService emailService;
    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);


    public EmailListenerService(
            BaseEmailService emailService
    ) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void sendEmail(
            EmailRequestedEvent event
    ) {
        emailService.sendEmail(event.mailBody());
        log.info(
                event.message(),
                event.userId()
        );
    }
}