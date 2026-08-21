package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import jakarta.mail.internet.MimeMessage;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.enums.EmailDeliveryStatus;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import mk.ukim.finki.gitcontributionanalyzer.service.EmailReportService;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import java.nio.charset.StandardCharsets;

@Service
public class EmailReportServiceImpl implements EmailReportService {

    private final AppSettings settings;
    private final MailProperties mailProperties;
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    public EmailReportServiceImpl(
            AppSettings settings,
            MailProperties mailProperties,
            JavaMailSender mailSender,
            SpringTemplateEngine templateEngine) {
        this.settings = settings;
        this.mailProperties = mailProperties;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public EmailDelivery sendReport(AnalysisReport report) {
        if (!settings.mailEnabled()) {
            return new EmailDelivery(
                    EmailDeliveryStatus.DISABLED,
                    "The report is ready, but email delivery is disabled in the application configuration."
            );
        }

        if (!hasMailConfiguration()) {
            return new EmailDelivery(
                    EmailDeliveryStatus.FAILED,
                    "SMTP settings are missing from the application configuration."
            );
        }

        try {
            String html = renderEmail(report);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(senderAddress());
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
                    EmailDeliveryStatus.SENT,
                    "The report was sent to " + report.requestedEmail() + "."
            );
        } catch (MailException | jakarta.mail.MessagingException exception) {
            return new EmailDelivery(
                    EmailDeliveryStatus.FAILED,
                    "The report is ready, but the SMTP server could not deliver the email."
            );
        }
    }

    private String renderEmail(AnalysisReport report) {
        Context context = new Context();
        context.setVariable("report", report);
        return templateEngine.process("email-report", context);
    }

    private boolean hasMailConfiguration() {
        return StringUtils.hasText(mailProperties.getHost())
                && StringUtils.hasText(mailProperties.getUsername())
                && StringUtils.hasText(mailProperties.getPassword())
                && StringUtils.hasText(senderAddress());
    }

    private String senderAddress() {
        return StringUtils.hasText(settings.mailFrom())
                ? settings.mailFrom()
                : mailProperties.getUsername();
    }

    private String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}