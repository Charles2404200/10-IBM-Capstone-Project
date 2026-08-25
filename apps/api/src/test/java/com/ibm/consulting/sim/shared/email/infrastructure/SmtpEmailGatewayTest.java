package com.ibm.consulting.sim.shared.email.infrastructure;

import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailGatewayTest {

    @Test
    void sendsMultipartMessageWithPlainTextHtmlAndReplyTo() throws Exception {
        SmtpEmailProperties properties = new SmtpEmailProperties();
        properties.setUsername("sender@example.test");
        properties.setPassword("app-password");
        properties.setFrom("Consulting Simulation <sender@example.test>");
        properties.setReplyTo("support@example.test");

        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);

        SmtpEmailGateway gateway = new SmtpEmailGateway(properties, sender);
        gateway.send(new OutboundEmail(
                "learner@example.test",
                "Confirm your account",
                "<p>HTML content</p>",
                "Plain text content"));

        ArgumentCaptor<MimeMessage> sentMessage = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sentMessage.capture());

        MimeMessage delivered = sentMessage.getValue();
        delivered.saveChanges();
        assertThat(delivered.getAllRecipients()[0].toString()).isEqualTo("learner@example.test");
        assertThat(delivered.getReplyTo()[0].toString()).isEqualTo("support@example.test");
        assertThat(delivered.getContentType()).containsIgnoringCase("multipart");
    }
}
