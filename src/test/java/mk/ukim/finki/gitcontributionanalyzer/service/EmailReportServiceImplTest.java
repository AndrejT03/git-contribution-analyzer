package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.enums.EmailDeliveryStatus;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.EmailReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailReportServiceImplTest {

    private AppSettings settings;
    private MailProperties mailProperties;
    private JavaMailSender mailSender;
    private SpringTemplateEngine templateEngine;
    private EmailReportServiceImpl service;

    @BeforeEach
    void setUp() {
        settings = appSettings(false, "");
        mailProperties = mock(MailProperties.class);
        mailSender = mock(JavaMailSender.class);
        templateEngine = mock(SpringTemplateEngine.class);
        service = new EmailReportServiceImpl(settings, mailProperties, mailSender, templateEngine);
    }

    @Test
    void returnsDisabledWithoutRenderingOrSending() {
        EmailDelivery result = service.sendReport(null);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.DISABLED);
        verifyNoInteractions(mailSender, templateEngine);
    }

    @Test
    void rejectsIncompleteSmtpConfigurationWithoutSending() {
        settings = appSettings(true, "");
        service = new EmailReportServiceImpl(settings, mailProperties, mailSender, templateEngine);
        when(mailProperties.getHost()).thenReturn("");

        EmailDelivery result = service.sendReport(null);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(result.message()).contains("SMTP settings are missing");
        verifyNoInteractions(mailSender, templateEngine);
    }

    private AppSettings appSettings(boolean mailEnabled, String mailFrom) {
        return new AppSettings(
                80,
                6000,
                120,
                "",
                "gemini-3.6-flash",
                180,
                mailEnabled,
                mailFrom
        );
    }
}