package com.audiosource.backend.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    private static final String EMAIL_SENDER = "audiosource.project@gmail.com";
    private static final String EMAIL_SUBJECT = "AudioSource : Your audio file is ready for download!";

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    public void testSendEmailToUser_Success() {
        String to = "recipient@example.com";
        String downloadUrl = "http://example.com/download";

        boolean result = notificationService.sendEmailToUser(to, downloadUrl);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals(EMAIL_SENDER, sentMessage.getFrom());
        assertEquals(to, sentMessage.getTo()[0]);
        assertEquals(EMAIL_SUBJECT, sentMessage.getSubject());
        assertEquals("You can download your separated files at: " + downloadUrl, sentMessage.getText());

        // Assert that the result is true, indicating the email was sent successfully
        assertTrue(result);
    }

    @Test
    public void testSendEmailToUser_FailureDueToMailException() {
        String to = "recipient@example.com";
        String downloadUrl = "http://example.com/download";

        // Simulate a MailException when mailSender.send() is called
        doThrow(new MailException("Test Mail Exception") {}).when(mailSender).send(any(SimpleMailMessage.class));

        boolean result = notificationService.sendEmailToUser(to, downloadUrl);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        assertFalse(result);
    }

    @Test
    public void testSendEmailToUser_NullEmailMessage() {
        NotificationService spyNotificationService = spy(notificationService);

        // Simulate a scenario where createSimpleMessage returns null
        doReturn(null).when(spyNotificationService).createSimpleMessage(anyString(), anyString());

        // Call the method
        boolean result = spyNotificationService.sendEmailToUser("recipient@example.com", "http://example.com/download");

        // Assert that the result is false, indicating email creation failed
        assertFalse(result);

        // Verify that mailSender.send was never called because message was null
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
