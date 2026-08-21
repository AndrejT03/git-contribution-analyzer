package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.ReportPdfServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.util.UUID;
import static mk.ukim.finki.gitcontributionanalyzer.support.TestDataFactory.richAnalysisReport;
import static org.assertj.core.api.Assertions.assertThat;

public class ReportPdfServiceImplTest {

    @Test
    void createsAReadablePdfFromTheCompleteReportTemplate() throws Exception {
        ReportPdfServiceImpl service = new ReportPdfServiceImpl(templateEngine());

        AnalysisReport report = richAnalysisReport(
                UUID.fromString("5cc61bf9-beb7-41bd-a016-8f50dc897706")
        );
        byte[] pdf = service.createPdf(report);

        assertThat(pdf).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(text)
                    .contains("CONTRIBUTION REPORT\nproject")
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
}