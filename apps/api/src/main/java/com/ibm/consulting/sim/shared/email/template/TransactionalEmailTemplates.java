package com.ibm.consulting.sim.shared.email.template;

import com.ibm.consulting.sim.shared.email.application.OutboundEmail;
import org.springframework.stereotype.Component;

/**
 * Central customisation point for transactional email copy and branding.
 * Add new templates here; delivery providers never own product-facing content.
 */
@Component
public class TransactionalEmailTemplates {

    private static final String PRODUCT_NAME = "IBM Consulting Simulation";

    public OutboundEmail verification(String recipient, String displayName, String verificationUrl) {
        return message(recipient, "Confirm your email address", displayName,
                "Confirm your email",
                "Welcome to " + PRODUCT_NAME + ". Confirm your email address to activate your account.",
                "Confirm email", verificationUrl,
                "This link expires in 24 hours. If you did not create this account, you can ignore this email.");
    }

    public OutboundEmail passwordReset(String recipient, String displayName, String resetUrl) {
        return message(recipient, "Reset your password", displayName,
                "Reset your password",
                "We received a request to reset the password for your " + PRODUCT_NAME + " account.",
                "Reset password", resetUrl,
                "This link expires in 15 minutes. If you did not request a reset, you can ignore this email.");
    }
    
    public OutboundEmail accountProvisioned(String recipient, String displayName, String setupUrl) {
        return message(recipient, "Set up your account", displayName,
                "Set up your account",
                "An administrator has created an account for the " + PRODUCT_NAME + ".",
                "Set up account", setupUrl,
                "This link expires in 24 hours. If you did not expect an account creation, you can ignore this email.");
    }

    private OutboundEmail message(String recipient, String subject, String displayName, String heading,
                                  String body, String actionLabel, String actionUrl, String note) {
        String safeName = escape(displayName);
        String safeBody = escape(body);
        String safeNote = escape(note);
        String html = """
                <!doctype html><html><body style="margin:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#161616">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr><td style="padding:32px 16px">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;margin:auto;background:#ffffff;border:1px solid #dfe3e8">
                <tr><td style="padding:28px 32px;border-top:4px solid #0f62fe"><p style="margin:0 0 22px;font-size:14px;color:#0f62fe;font-weight:700">%s</p>
                <h1 style="margin:0 0 16px;font-size:26px;line-height:1.25">%s</h1><p style="font-size:16px;line-height:1.55">Hi %s,</p>
                <p style="font-size:16px;line-height:1.55">%s</p><p style="margin:28px 0"><a href="%s" style="display:inline-block;background:#0f62fe;color:#ffffff;text-decoration:none;padding:13px 20px;font-weight:700">%s</a></p>
                <p style="font-size:14px;line-height:1.5;color:#525252">%s</p></td></tr></table></td></tr></table></body></html>
                """.formatted(PRODUCT_NAME, escape(heading), safeName, safeBody, escape(actionUrl), escape(actionLabel), safeNote);
        String text = "Hi " + displayName + ",\n\n" + body + "\n\n" + actionLabel + ": " + actionUrl + "\n\n" + note;
        return new OutboundEmail(recipient, subject, html, text);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
