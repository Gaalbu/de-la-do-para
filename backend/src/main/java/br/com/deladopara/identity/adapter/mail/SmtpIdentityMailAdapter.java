package br.com.deladopara.identity.adapter.mail;

import br.com.deladopara.identity.application.IdentityMailPort;
import br.com.deladopara.identity.config.IdentityProperties;
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
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Confirme seu e-mail — De Lá do Pará");
        message.setText("Para confirmar seu e-mail, abra este link em até "
                + properties.verificationTokenTtl().toMinutes() + " minutos:\n\n"
                + verificationUrl + "\n\nSe você não criou esta conta, ignore esta mensagem.");
        mailSender.send(message);
    }
}
