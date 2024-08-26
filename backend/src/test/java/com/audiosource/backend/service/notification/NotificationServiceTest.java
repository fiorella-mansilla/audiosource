package com.audiosource.backend.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    public void testSendSimpleEmail() {
        String to = "recipient@example.com";
        String subject = "subject";
        String body = "body";

        notificationService.sendSimpleEmail(to, subject, body);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
