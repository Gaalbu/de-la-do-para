package br.com.deladopara.identity.infrastructure;

import br.com.deladopara.identity.application.IdentityMailQueue;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
@ConditionalOnProperty(prefix = "identity.mail.worker", name = "enabled", havingValue = "true")
public class IdentityMailWorker {

    private static final Logger log = LoggerFactory.getLogger(IdentityMailWorker.class);
    private final IdentityMailQueue queue;
    private final JavaMailSender mailSender;
    private final Duration lease;
    private final Duration retryDelay;
    private final String from;
    private final String frontendBaseUrl;
    private final int batchSize;

    public IdentityMailWorker(
            IdentityMailQueue queue,
            JavaMailSender mailSender,
            @Value("${identity.mail.worker.lease:PT2M}") Duration lease,
            @Value("${identity.mail.worker.retry-delay:PT30S}") Duration retryDelay,
            @Value("${identity.mail.from:no-reply@deladopara.local}") String from,
            @Value("${identity.mail.frontend-base-url:http://localhost:4200}") String frontendBaseUrl,
            @Value("${identity.mail.worker.batch-size:10}") int batchSize) {
        if (lease.isNegative() || lease.isZero() || retryDelay.isNegative() || retryDelay.isZero() || batchSize < 1) {
            throw new IllegalArgumentException("Invalid identity mail worker settings");
        }
        this.queue = queue;
        this.mailSender = mailSender;
        this.lease = lease;
        this.retryDelay = retryDelay;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${identity.mail.worker.poll-delay:PT2S}")
    public void poll() {
        for (int i = 0; i < batchSize; i++) {
            UUID id = queue.claimOne(lease);
            if (id == null) {
                return;
            }
            deliver(id);
        }
    }

    private void deliver(UUID id) {
        try {
            var content = queue.loadMessage(id);
            if (content == null) {
                return;
            }
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(content.recipient());
            if (content.type() == br.com.deladopara.identity.domain.VerificationToken.TokenType.VERIFY) {
                message.setSubject("Confirme seu e-mail — De Lá do Pará");
                message.setText("Acesse " + frontendBaseUrl + "/verify-email#token=" + content.token()
                        + " para confirmar seu e-mail. Se você não criou a conta, ignore esta mensagem.");
            } else {
                message.setSubject("Redefina sua senha — De Lá do Pará");
                message.setText("Acesse " + frontendBaseUrl + "/reset-password#token=" + content.token()
                        + " para redefinir sua senha. Se você não solicitou, ignore esta mensagem.");
            }
            mailSender.send(message);
            queue.accepted(id);
        } catch (org.springframework.mail.MailException e) {
            queue.retry(id, retryDelay, safeErrorCode(e));
            log.warn(
                    "Falha ao enviar e-mail de verificação; item={} tipo={}",
                    id,
                    e.getClass().getSimpleName());
        }
    }

    private static String safeErrorCode(org.springframework.mail.MailException e) {
        return e.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_.-]", "_").toUpperCase(Locale.ROOT);
    }
}
