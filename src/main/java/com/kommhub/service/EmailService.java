package com.kommhub.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final String FROM_NAME = "Komm";

    private final JavaMailSender mailSender;

    private String verificationTemplate;
    private String passwordResetTemplate;

    @PostConstruct
    void loadTemplate() throws IOException {
        verificationTemplate = new ClassPathResource("email/verification.html")
                .getContentAsString(StandardCharsets.UTF_8);
        passwordResetTemplate = new ClassPathResource("email/password-reset.html")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public void sendVerificationEmail(String toEmail, String username, String code) {
        try {
            String html = verificationTemplate
                    .replace("{{username}}", username)
                    .replace("{{code_boxes}}", buildCodeBoxesHtml(code));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, FROM_NAME);
            helper.setTo(toEmail);
            helper.setSubject("Verify your Komm email address");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Verification email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email. Please try again later.");
        }
    }

    public void sendPasswordResetEmail(String toEmail, String username, String resetLink) {
        try {
            String html = passwordResetTemplate
                    .replace("{{username}}", username)
                    .replace("{{reset_link}}", resetLink);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, FROM_NAME);
            helper.setTo(toEmail);
            helper.setSubject("Reset your Komm password");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send password reset email. Please try again later.");
        }
    }

    /** Forwards a closed-beta access request from the public site to the hub operator. */
    public void sendBetaAccessRequest(String recipientEmail, String requesterEmail, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail, FROM_NAME);
            helper.setTo(recipientEmail);
            helper.setReplyTo(requesterEmail);
            helper.setSubject("Komm beta access request from " + requesterEmail);
            helper.setText("Beta access request\n\nFrom: " + requesterEmail + "\n\n" + message, false);
            mailSender.send(mimeMessage);
            log.info("Beta access request forwarded for: {}", requesterEmail);
        } catch (Exception e) {
            log.error("Failed to forward beta access request from {}: {}", requesterEmail, e.getMessage());
            throw new RuntimeException("Failed to send your request. Please try again later.");
        }
    }

    private String buildCodeBoxesHtml(String code) {
        StringBuilder sb = new StringBuilder();
        for (char digit : code.toCharArray()) {
            sb.append(String.format(
                    "<td style=\"padding:0 5px;\">" +
                            "<span style=\"display:inline-block;width:52px;height:64px;" +
                            "background-color:#1c1c1e;border:2px solid #9580ff;border-radius:8px;" +
                            "text-align:center;line-height:64px;font-size:30px;font-weight:700;" +
                            "color:#9580ff;font-family:monospace;\">%s</span></td>",
                    digit));
        }
        return sb.toString();
    }
}
