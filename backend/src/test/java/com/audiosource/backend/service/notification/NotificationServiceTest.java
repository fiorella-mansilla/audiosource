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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private final String recipient = "recipient@example.com";
    private final String downloadUrl = "http://example.com/download";

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    public void testSendEmailToUser_Success() {

        boolean result = notificationService.sendEmailToUser(recipient, downloadUrl);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertAll("Validating sent email",
                () -> assertEquals(EMAIL_SENDER, sentMessage.getFrom(), "Sender email should match"),
                () -> assertEquals(recipient, sentMessage.getTo()[0], "Recipient email should match"),
                () -> assertEquals(EMAIL_SUBJECT, sentMessage.getSubject(), "Email subject should match"),
                () -> assertEquals("You can download your separated files at: " + downloadUrl, sentMessage.getText(), "Email body should contain the download URL")
        );

        // Assert that the result is true, indicating the email was sent successfully
        assertTrue(result, "The result should be true when the email is sent successfully");
    }

    @Test
    public void testSendEmailToUser_FailureDueToMailException() {

        // Simulate a MailException when mailSender.send() is called
        doThrow(new MailException("Test Mail Exception") {}).when(mailSender).send(any(SimpleMailMessage.class));

        boolean result = notificationService.sendEmailToUser(recipient, downloadUrl);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        assertFalse(result, "The result should be false when sending email fails");
    }

    @Test
    public void testSendEmailToUser_NullEmailMessage() {

        NotificationService spyNotificationService = spy(notificationService);

        // Simulate a scenario where createSimpleMessage returns null
        doReturn(null).when(spyNotificationService).createSimpleMessage(anyString(), anyString());

        boolean result = spyNotificationService.sendEmailToUser("recipient@example.com", "http://example.com/download");

        assertFalse(result, "The result should be false when createSimpleMessage returns null");

        // Verify that mailSender.send was never called because message was null
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    public void testCreateSimpleMessage_ValidUserInput_ShouldReturnMessage() {

        SimpleMailMessage message = notificationService.createSimpleMessage(recipient, downloadUrl);

        assertAll("Validating SimpleMailMessage creation",
                () -> assertNotNull(message, "Message should not be null"),
                () -> assertEquals(EMAIL_SENDER, message.getFrom(), "Sender email should match"),
                () -> assertEquals(recipient, message.getTo()[0], "Recipient email should match"),
                () -> assertEquals(EMAIL_SUBJECT, message.getSubject(), "Email subject should match"),
                () -> assertEquals("You can download your separated files at: " + downloadUrl, message.getText(), "Email body should contain the download URL")
        );
    }

    @Test
    public void testSendEmailToUser_InvalidEmail_ShouldStillAttemptToSend() {
        String invalidEmail = "invalid-email";

        boolean result = notificationService.sendEmailToUser(invalidEmail, downloadUrl);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertAll("Validating send attempt with invalid email",
                () -> assertEquals(invalidEmail, sentMessage.getTo()[0], "Invalid email should still be used in the message"),
                () -> assertTrue(result, "Result should be true since the email was still attempted to be sent")
        );
    }

    @Test
    public void testSendEmailToUser_NullDownloadUrl_ShouldFailToSend() {

        boolean result = notificationService.sendEmailToUser(recipient, null);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        assertFalse(result, "The result should be false when download URL is null");
    }
}
