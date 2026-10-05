package br.com.deladopara.notifications.application;

import br.com.deladopara.eventing.application.EventRetryPolicy;
import br.com.deladopara.notifications.adapter.persistence.OrderNotificationRepository;
import jakarta.mail.MessagingException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

@Component
@Profile("worker")
@ConditionalOnProperty(prefix = "notifications.mail.worker", name = "enabled", havingValue = "true")
public class OrderNotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationWorker.class);
    private final OrderNotificationRepository notifications;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final Duration lease;
    private final EventRetryPolicy retryPolicy;
    private final String from;
    private final int batchSize;
    private final TransactionTemplate transactions;

    public OrderNotificationWorker(
            OrderNotificationRepository notifications,
            JavaMailSender mailSender,
            Clock clock,
            @Value("${notifications.mail.worker.lease:PT2M}") Duration lease,
            @Value("${identity.mail.from:no-reply@deladopara.local}") String from,
            @Value("${notifications.mail.worker.batch-size:10}") int batchSize,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            EventRetryPolicy retryPolicy) {
        if (lease.isNegative() || lease.isZero() || batchSize < 1) {
            throw new IllegalArgumentException("Invalid order notification worker settings");
        }
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.clock = clock;
        this.lease = lease;
        this.retryPolicy = retryPolicy;
        this.from = from;
        this.batchSize = batchSize;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${notifications.mail.worker.poll-delay:PT2S}")
    public void poll() {
        for (int i = 0; i < batchSize; i++) {
            var message = claim();
            if (message == null) {
                return;
            }
            deliver(message);
        }
    }

    public OrderNotificationRepository.QueuedMail claim() {
        return transactions.execute(status -> notifications
                .claim(clock.instant(), clock.instant().plus(lease))
                .orElse(null));
    }

    private void deliver(OrderNotificationRepository.QueuedMail mail) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(mail.recipient());
            helper.setSubject("Atualização do pedido — De Lá do Pará");
            helper.setText(render(mail), renderHtml(mail));
            mailSender.send(message);
            try {
                transactions.executeWithoutResult(status -> notifications.accepted(mail.id(), clock.instant()));
            } catch (RuntimeException persistenceFailure) {
                transactions.executeWithoutResult(
                        status -> notifications.unknown(mail.id(), "ACCEPTANCE_RECORD_FAILED"));
                log.error("Order notification acceptance could not be recorded; item={}", mail.id());
            }
        } catch (MessagingException invalidMessage) {
            transactions.executeWithoutResult(status -> notifications.failed(mail.id(), "MIME_BUILD_FAILED"));
            log.error("Order notification could not be rendered; item={}", mail.id());
        } catch (org.springframework.mail.MailAuthenticationException
                | org.springframework.mail.MailParseException
                | org.springframework.mail.MailPreparationException permanent) {
            var code = safeCode(permanent);
            log.warn("Order notification SMTP rejected permanently; item={} error={}", mail.id(), code);
            transactions.executeWithoutResult(status -> notifications.failed(mail.id(), code));
        } catch (org.springframework.mail.MailSendException uncertain) {
            var code = safeCode(uncertain);
            if (knownConnectionFailure(uncertain)) {
                scheduleRetry(mail, code);
            } else {
                log.warn("Order notification SMTP outcome unknown; item={} error={}", mail.id(), code);
                transactions.executeWithoutResult(status -> notifications.unknown(mail.id(), code));
            }
        } catch (org.springframework.mail.MailException transientFailure) {
            scheduleRetry(mail, safeCode(transientFailure));
        }
    }

    private void scheduleRetry(OrderNotificationRepository.QueuedMail mail, String code) {
        if (retryPolicy.exhausted(EventRetryPolicy.FailureKind.TRANSIENT, mail.attemptCount())) {
            transactions.executeWithoutResult(status -> notifications.failed(mail.id(), "EXHAUSTED_" + code));
            return;
        }
        var delay = retryPolicy.delay(mail.attemptCount());
        transactions.executeWithoutResult(
                status -> notifications.retry(mail.id(), clock.instant().plus(delay), code));
        log.warn("Order notification SMTP failed; item={} attempt={} error={}", mail.id(), mail.attemptCount(), code);
    }

    private static boolean knownConnectionFailure(org.springframework.mail.MailSendException failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new java.util.ArrayDeque<Throwable>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof UnknownHostException) {
                return true;
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            if (current instanceof MessagingException messaging && messaging.getNextException() != null) {
                pending.addLast(messaging.getNextException());
            }
            if (current == failure) {
                failure.getFailedMessages().values().stream()
                        .filter(java.util.Objects::nonNull)
                        .forEach(pending::addLast);
            }
        }
        return false;
    }

    private static String safeCode(org.springframework.mail.MailException failure) {
        return failure.getClass()
                .getSimpleName()
                .replaceAll("[^A-Za-z0-9_.-]", "_")
                .toUpperCase(Locale.ROOT);
    }

    private static String render(OrderNotificationRepository.QueuedMail mail) {
        var status = "order.created".equals(mail.eventType()) ? "recebido" : mail.orderStatus();
        return "Este pedido é uma demonstração da aplicação De Lá do Pará.\n"
                + "Pedido: " + mail.orderId() + "\n"
                + "Estado: " + status + "\n"
                + "Total: R$ " + String.format(Locale.ROOT, "%.2f", mail.totalCents() / 100.0) + "\n"
                + "Modalidade: " + mail.fulfillmentMode() + "\n"
                + pickupText(mail)
                + "Consulte o pedido pela loja usando o acesso já disponível no navegador.\n"
                + "Esta mensagem não contém links de acesso ou dados de pagamento.";
    }

    private static String renderHtml(OrderNotificationRepository.QueuedMail mail) {
        var status = "order.created".equals(mail.eventType()) ? "recebido" : mail.orderStatus();
        return "<html><body><main><h1>Atualização do pedido</h1>"
                + "<p>Este pedido é uma demonstração da aplicação De Lá do Pará.</p>"
                + "<dl><dt>Pedido</dt><dd>" + mail.orderId() + "</dd>"
                + "<dt>Estado</dt><dd>" + status + "</dd>"
                + "<dt>Total</dt><dd>R$ " + String.format(Locale.ROOT, "%.2f", mail.totalCents() / 100.0)
                + "</dd><dt>Modalidade</dt><dd>" + mail.fulfillmentMode() + "</dd></dl>"
                + pickupHtml(mail)
                + "<p>Consulte o pedido pela loja usando o acesso já disponível no navegador.</p>"
                + "<p>Esta mensagem não contém links de acesso ou dados de pagamento.</p></main></body></html>";
    }

    private static String pickupText(OrderNotificationRepository.QueuedMail mail) {
        if (!hasPickupInstructions(mail)) {
            return "";
        }
        return "Ponto de retirada: " + mail.pickupPoint() + "\n"
                + "Horário de retirada: " + mail.pickupWindow() + "\n"
                + "Código de retirada: consulte somente na tela segura do pedido; não o enviamos por e-mail.\n";
    }

    private static String pickupHtml(OrderNotificationRepository.QueuedMail mail) {
        if (!hasPickupInstructions(mail)) {
            return "";
        }
        return "<p>Ponto de retirada: " + HtmlUtils.htmlEscape(mail.pickupPoint()) + "</p>"
                + "<p>Horário de retirada: " + HtmlUtils.htmlEscape(mail.pickupWindow()) + "</p>"
                + "<p>Código de retirada: consulte somente na tela segura do pedido; não o enviamos por e-mail.</p>";
    }

    private static boolean hasPickupInstructions(OrderNotificationRepository.QueuedMail mail) {
        return "READY_FOR_PICKUP".equals(mail.orderStatus())
                && "PICKUP".equals(mail.fulfillmentMode())
                && mail.pickupPoint() != null
                && !mail.pickupPoint().isBlank()
                && mail.pickupWindow() != null
                && !mail.pickupWindow().isBlank();
    }
}
