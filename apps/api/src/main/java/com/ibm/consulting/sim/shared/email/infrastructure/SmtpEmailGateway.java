package com.ibm.consulting.sim.shared.email.infrastructure;

import com.ibm.consulting.sim.shared.email.application.EmailDeliveryGateway;
import com.ibm.consulting.sim.shared.email.application.EmailDeliveryUnavailableException;
import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** SMTP adapter for providers such as Gmail. Credentials are supplied only through environment variables. */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "smtp")
public class SmtpEmailGateway implements EmailDeliveryGateway {

    private final SmtpEmailProperties properties;
    private final JavaMailSender sender;

    @Autowired
    public SmtpEmailGateway(SmtpEmailProperties properties) {
        this(properties, createSender(properties));
    }

    SmtpEmailGateway(SmtpEmailProperties properties, JavaMailSender sender) {
        this.properties = properties;
        this.sender = sender;
    }

    @Override
    public void send(OutboundEmail email) {
        validateConfiguration();
        try {
            MimeMessage message = sender.createMimeMessage();
            // Verification and reset messages have text and HTML alternatives.
            // Multipart mode is required for those alternatives and for Reply-To.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFrom());
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            if (!properties.getReplyTo().isBlank()) {
                helper.setReplyTo(properties.getReplyTo());
            }
            sender.send(message);
        } catch (MailException | MessagingException ex) {
            throw new EmailDeliveryUnavailableException("Email delivery is temporarily unavailable.", ex);
        }
    }

    private void validateConfiguration() {
        if (properties.getUsername().isBlank() || properties.getPassword().isBlank() || properties.getFrom().isBlank()) {
            throw new EmailDeliveryUnavailableException(
                    "SMTP email delivery is not configured. Set SMTP_USERNAME, SMTP_PASSWORD and SMTP_FROM.");
        }
    }

    private static JavaMailSenderImpl createSender(SmtpEmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties sessionProperties = sender.getJavaMailProperties();
        sessionProperties.put("mail.transport.protocol", "smtp");
        sessionProperties.put("mail.smtp.auth", "true");
        sessionProperties.put("mail.smtp.starttls.enable", "true");
        sessionProperties.put("mail.smtp.starttls.required", "true");
        sessionProperties.put("mail.smtp.connectiontimeout", properties.getConnectionTimeoutMs());
        sessionProperties.put("mail.smtp.timeout", properties.getTimeoutMs());
        sessionProperties.put("mail.smtp.writetimeout", properties.getTimeoutMs());
        return sender;
    }
}
