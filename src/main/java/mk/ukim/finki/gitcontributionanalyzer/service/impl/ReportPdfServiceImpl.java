package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportPdfService;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

@Service
public class ReportPdfServiceImpl implements ReportPdfService {

    private static final String PDF_TEMPLATE = "report-pdf";
    private static final String PDF_FONT_FAMILY = "Liberation Sans";
    private static final String PDF_FONT_RESOURCE =
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private final SpringTemplateEngine templateEngine;

    public ReportPdfServiceImpl(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public byte[] createPdf(AnalysisReport report) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("report", report);
        context.setVariable("commitCategories", CommitCategory.values());
        String html = templateEngine.process(PDF_TEMPLATE, context);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withProducer("Git Contribution AI");
            builder.useFont(this::openPdfFont, PDF_FONT_FAMILY);
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("The PDF report could not be generated.", exception);
        }
    }

    private InputStream openPdfFont() {
        InputStream font = getClass().getClassLoader().getResourceAsStream(PDF_FONT_RESOURCE);
        if (font == null) {
            throw new IllegalStateException("The PDF font resource is unavailable.");
        }
        return font;
    }
}