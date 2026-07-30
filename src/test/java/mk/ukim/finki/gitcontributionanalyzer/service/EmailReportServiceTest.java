package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailReportServiceTest {

    private AppSettings settings;
    private SpringTemplateEngine templateEngine;
    private EmailReportService service;

    @BeforeEach
    void setUp() {
        settings = mock(AppSettings.class);
        templateEngine = mock(SpringTemplateEngine.class);
        service = new EmailReportService(settings, templateEngine);
    }

    @Test
    void returnsDisabledWithoutRenderingOrSending() {
        when(settings.mailEnabled()).thenReturn(false);

        EmailDelivery result = service.sendReport(null);

        assertThat(result.status()).isEqualTo("DISABLED");
        verifyNoInteractions(templateEngine);
    }

    @Test
    void rejectsIncompleteSmtpConfigurationWithoutSending() {
        when(settings.mailEnabled()).thenReturn(true);
        when(settings.mailHost()).thenReturn("");

        EmailDelivery result = service.sendReport(null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("SMTP settings are missing");
        verifyNoInteractions(templateEngine);
    }
}