package br.com.deladopara.identity.adapter.mail;

import br.com.deladopara.identity.application.IdentityMailPort;
import br.com.deladopara.identity.config.IdentityProperties;
import java.time.Duration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpIdentityMailAdapter implements IdentityMailPort {

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    public SmtpIdentityMailAdapter(JavaMailSender mailSender, IdentityProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerification(String email, String verificationUrl) {
        send(
                email,
                "Confirme seu e-mail — De Lá do Pará",
                "Para confirmar seu e-mail, abra este link em até "
                        + properties.verificationTokenTtl().toMinutes() + " minutos:\n\n"
                        + verificationUrl + "\n\nSe você não criou esta conta, ignore esta mensagem.");
    }

    @Override
    public void sendRecovery(String email, String recoveryUrl, Duration tokenTtl) {
        send(
                email,
                "Redefina sua senha — De Lá do Pará",
                "Para redefinir sua senha, abra este link em até "
                        + tokenTtl.toMinutes() + " minutos:\n\n"
                        + recoveryUrl
                        + "\n\nO link é de uso único. Se você não solicitou a redefinição, ignore esta mensagem.");
    }

    private void send(String email, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
