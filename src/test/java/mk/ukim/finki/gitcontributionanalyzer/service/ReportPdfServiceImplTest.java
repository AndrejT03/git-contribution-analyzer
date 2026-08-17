package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.ReportPdfServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

public class ReportPdfServiceImplTest {

    @Test
    void createsAReadablePdfFromTheCompleteReportTemplate() throws Exception {
        ReportPdfServiceImpl service = new ReportPdfServiceImpl(templateEngine());

        byte[] pdf = service.createPdf(sampleReport());

        assertThat(pdf).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(text)
                    .contains("team/project")
                    .contains("поддршка")
                    .contains("Contribution overview")
                    .contains("Ana Developer")
                    .contains("Commit evidence - Ana Developer")
                    .contains("Final contribution assessment")
                    .contains("Methodology")
                    .contains("Disclaimer");
        }
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(TemplateMode.HTML);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private AnalysisReport sampleReport() {
        ContributorAnalysis contributor = new ContributorAnalysis(
                "Ana Developer",
                "ana@example.com",
                100,
                ContributionLevel.HIGH,
                "Implemented the core project functionality.",
                List.of("Authentication", "User profile"),
                List.of(new CategorySummary(
                        CommitCategory.FUNCTIONAL,
                        1,
                        "Core product functionality."
                )),
                List.of(new CommitAnalysis(
                        "1234567890abcdef",
                        "Add authentication flow",
                        CommitCategory.FUNCTIONAL,
                        5,
                        "Directly implements the main project goal."
                )),
                List.of("Core ownership is concentrated in one contributor.")
        );
        ContributionAnalysis analysis = new ContributionAnalysis(
                "A collaboration application for student teams.",
                "The implementation aligns with the stated project goal.",
                List.of(contributor),
                List.of(new TeamIndicator(
                        "ownership",
                        TeamIndicatorSeverity.INFO,
                        "Clear ownership",
                        "The main feature has a clear owner."
                )),
                "The analyzed work delivers the main project goal.",
                "Commits were categorized and weighted by scope and importance."
        );
        return new AnalysisReport(
                UUID.fromString("5cc61bf9-beb7-41bd-a016-8f50dc897706"),
                "https://github.com/team/project",
                "team/project",
                "main",
                "Build a collaboration and organization application with поддршка for student teams.",
                "mentor@example.com",
                AnalysisSource.GEMINI,
                "gemini-3.7-flash",
                "Gemini completed the analysis.",
                1,
                OffsetDateTime.parse("2026-08-10T12:00:00+02:00"),
                analysis,
                new EmailDelivery(EmailDeliveryStatus.SENT, "The report was sent.")
        );
    }
}