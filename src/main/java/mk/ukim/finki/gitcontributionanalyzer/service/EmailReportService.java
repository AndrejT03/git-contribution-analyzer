package mk.ukim.finki.gitcontributionanalyzer.service;
import jakarta.mail.internet.MimeMessage;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
public class EmailReportService {

    private final AppSettings settings;
    private final SpringTemplateEngine templateEngine;

    public EmailReportService(AppSettings settings, SpringTemplateEngine templateEngine) {
        this.settings = settings;
        this.templateEngine = templateEngine;
    }

    public EmailDelivery sendReport(AnalysisReport report) {
        if(!settings.mailEnabled()) {
            return new EmailDelivery(
                    "DISABLED",
                    "The report is ready, but email delivery is disabled in .env."
            );
        }

        if(!hasMailConfiguration()) {
            return new EmailDelivery(
                    "FAILED",
                    "SMTP settings are missing from the .env file."
            );
        }

        try {
            String html = renderEmail(report);
            JavaMailSenderImpl mailSender = createMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(settings.mailFrom());
            helper.setTo(report.requestedEmail());
            helper.setSubject("Git Contribution AI report · " + report.repositoryName());
            helper.setText(html, true);
            helper.addAttachment(
                    "git-contribution-" + safeFileName(report.repositoryName()) + ".html",
                    new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)),
                    "text/html; charset=UTF-8"
            );

            mailSender.send(message);
            return new EmailDelivery(
                    "SENT",
                    "The report was sent to " + report.requestedEmail() + "."
            );

        } catch (MailException | jakarta.mail.MessagingException e) {
            return new EmailDelivery(
                    "FAILED",
                    "The report is ready, but the SMTP server could not deliver the email."
            );
        }
    }

    public String renderEmail(AnalysisReport report) {
        Context context = new Context();
        context.setVariable("report", report);
        return templateEngine.process("email-report", context);
    }

    private JavaMailSenderImpl createMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.mailHost());
        mailSender.setPort(settings.mailPort());
        mailSender.setUsername(settings.mailUsername());
        mailSender.setPassword(settings.mailPassword());

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return mailSender;
    }

    public boolean hasMailConfiguration() {
        return !settings.mailHost().isBlank()
                && !settings.mailUsername().isBlank()
                && !settings.mailPassword().isBlank()
                && !settings.mailFrom().isBlank();
    }

    private String safeFileName(String name) { return name.replaceAll("[^a-zA-Z0-9._-]", "-"); }
}