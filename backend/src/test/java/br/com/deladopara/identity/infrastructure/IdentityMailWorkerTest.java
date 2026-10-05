package br.com.deladopara.identity.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.identity.application.IdentityMailQueue;
import br.com.deladopara.identity.domain.VerificationToken;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class IdentityMailWorkerTest {

    @Test
    void smtpFailureSchedulesRetryWithoutLoggingToken() {
        var queue = mock(IdentityMailQueue.class);
        var sender = mock(JavaMailSender.class);
        var id = UUID.randomUUID();
        when(queue.claimOne(any())).thenReturn(id, (UUID) null);
        when(queue.loadMessage(id))
                .thenReturn(new IdentityMailQueue.MailMessage(
                        "customer@example.com", VerificationToken.TokenType.VERIFY, "secret-token"));
        doThrow(new MailSendException("smtp rejected")).when(sender).send(any(SimpleMailMessage.class));
        var worker = new IdentityMailWorker(
                queue,
                sender,
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                "no-reply@example.com",
                "http://localhost:4200",
                1);

        worker.poll();

        verify(queue).retry(eq(id), eq(Duration.ofSeconds(30)), eq("MAILSENDEXCEPTION"));
        verify(queue, never()).accepted(id);
    }

    @Test
    void relayAcceptanceMarksMessageSentAndLinkKeepsTokenInFragment() {
        var queue = mock(IdentityMailQueue.class);
        var sender = mock(JavaMailSender.class);
        var id = UUID.randomUUID();
        when(queue.claimOne(any())).thenReturn(id, (UUID) null);
        when(queue.loadMessage(id))
                .thenReturn(new IdentityMailQueue.MailMessage(
                        "customer@example.com", VerificationToken.TokenType.VERIFY, "secret-token"));
        var worker = new IdentityMailWorker(
                queue,
                sender,
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                "no-reply@example.com",
                "https://shop.example",
                1);

        worker.poll();

        verify(queue).accepted(id);
        verify(sender)
                .send(org.mockito.ArgumentMatchers.<SimpleMailMessage>argThat(
                        mail -> mail.getText().contains("/verify-email#token=secret-token")
                                && !mail.getText().contains("?token=")));
    }
}
