package com.securevoting.service;

import com.securevoting.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final AppProperties props;

    public MailService(JavaMailSender sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    public void sendRegistrationOtp(String toEmail, String displayName, String otp, int ttlMinutes) {
        send(toEmail,
                "Verify your Secure Voting account",
                otpHtml(displayName, otp, ttlMinutes, "complete your registration"));
    }

    public void sendVotingOtp(String toEmail, String electionTitle, String otp, int ttlMinutes) {
        send(toEmail,
                "Your voting code for " + electionTitle,
                otpHtml(electionTitle, otp, ttlMinutes, "cast your vote"));
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(props.getMail().getFrom(), props.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(mime);
            log.info("Sent OTP email to {}", maskEmail(to));
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Failed to send OTP email to {}: {}", maskEmail(to), e.getMessage());
            throw new IllegalStateException("Could not send OTP email. Check SMTP configuration.", e);
        }
    }

    private String otpHtml(String recipientLabel, String otp, int ttlMinutes, String action) {
        return """
               <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto;
                           border: 1px solid #e3e3e3; border-radius: 8px; padding: 24px;">
                   <h2 style="color:#0b3d91; margin-top:0;">Secure Voting System</h2>
                   <p>Hello %s,</p>
                   <p>Use the verification code below to %s. The code expires in
                      <strong>%d minutes</strong>.</p>
                   <div style="font-size: 32px; font-weight: bold; letter-spacing: 6px;
                               text-align:center; padding:16px; background:#f4f6fb;
                               border-radius:6px; color:#0b3d91;">%s</div>
                   <p style="color:#666; font-size: 12px; margin-top:24px;">
                     If you didn't request this, you can safely ignore this email.
                   </p>
               </div>
               """.formatted(escape(recipientLabel), escape(action), ttlMinutes, escape(otp));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}