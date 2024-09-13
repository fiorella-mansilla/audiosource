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

    public boolean sendEmailToUser(String to, String downloadUrl) {
        SimpleMailMessage message = createSimpleMessage(to, downloadUrl);
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            LOGGER.error("Failed to send email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private SimpleMailMessage createSimpleMessage(String to, String downloadUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(EMAIL_SENDER);
        message.setTo(to);
        message.setSubject(EMAIL_SUBJECT);
        message.setText("You can download your separated files at: " + downloadUrl);
        return message;
    }
}
