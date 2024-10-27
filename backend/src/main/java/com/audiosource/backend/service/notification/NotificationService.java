package com.audiosource.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final String EMAIL_SENDER = "audiosource.project@gmail.com";
    private static final String EMAIL_SUBJECT = "AudioSource : Your audio file is ready for download!";
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private final JavaMailSender mailSender;

    @Autowired
    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Email the user with the download URL
    public boolean sendEmailToUser(String userEmail, String downloadUrl) {

        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            LOGGER.error("Download URL is null or empty for recipient: {}", userEmail);
            return false;  // If the URL is null or empty, return false immediately.
        }

        SimpleMailMessage message = createSimpleMessage(userEmail, downloadUrl);
        if (message == null) {
            LOGGER.error("Failed to create email message for recipient: {}", userEmail);
            return false;
        }

        try {
            mailSender.send(message);
            LOGGER.info("Email sent successfully to: {}", userEmail);
            return true;
        } catch (MailException e) {
            LOGGER.error("Failed to send email to {}: {}", userEmail, e.getMessage());
            return false;
        }
    }

    // Create a simple mail message with the user's email and the download URL added to the body
    public SimpleMailMessage createSimpleMessage(String to, String downloadUrl) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(EMAIL_SENDER);
        message.setTo(to);
        message.setSubject(EMAIL_SUBJECT);
        message.setText("You can download your separated files at: " + downloadUrl);

        return message;
    }
}
