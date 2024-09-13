package com.audiosource.backend.service.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final String EMAIL_SENDER = "audiosource.project@gmail.com";
    private static final String EMAIL_SUBJECT = "Your audio file is ready for download!";

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmailToUser(String to, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(EMAIL_SENDER);
        message.setTo(to);
        message.setSubject(EMAIL_SUBJECT);
        message.setText(body);

        mailSender.send(message);
    }
}
